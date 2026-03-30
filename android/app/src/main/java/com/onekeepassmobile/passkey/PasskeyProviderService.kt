package com.onekeepassmobile.passkey

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginCreatePublicKeyCredentialRequest
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import androidx.credentials.provider.PublicKeyCredentialEntry
import com.onekeepassmobile.DbServiceAPI
import com.onekeepassmobile.R
import org.json.JSONObject
import java.util.concurrent.Executors

// Android 14+ (API 34) Credential Provider for OneKeePass passkeys.
// Implements assertion (sign-in) and registration (creation) flows.
// All credential handling is delegated to PasskeyActivity via PendingIntents.
//
// Registered in AndroidManifest.xml with:
//   android:permission="android.permission.BIND_CREDENTIAL_PROVIDER_SERVICE"
// Guarded by @bool/use_credential_manager (true only on API 34+).

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class PasskeyProviderService : CredentialProviderService() {

    private val executor = Executors.newSingleThreadExecutor()

    // ── Assertion ─────────────────────────────────────────────────────────

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>
    ) {
        executor.execute {
            try {
                val entries = mutableListOf<androidx.credentials.provider.CredentialEntry>()

                for (option in request.beginGetCredentialOptions) {
                    if (option !is BeginGetPublicKeyCredentialOption) continue

                    val (rpId, allowIds) = parseAssertionRequest(option.requestJson)
                    // Always offer at least a generic entry even when rpId is absent
                    val passkeys = if (rpId.isNotEmpty()) findMatchingPasskeys(rpId, allowIds)
                                   else emptyList()

                    if (passkeys.isNotEmpty()) {
                        for (pk in passkeys) {
                            entries.add(
                                buildAssertionEntry(pk.entryUuid, pk.dbKey, pk.username, option)
                            )
                        }
                    } else {
                        // DB locked, no matching passkeys, or rpId unknown: offer generic unlock entry
                        entries.add(buildGenericAssertionEntry(option))
                    }
                }

                callback.onResult(BeginGetCredentialResponse(entries))
            } catch (e: Exception) {
                Log.e(TAG, "onBeginGetCredentialRequest error", e)
                callback.onError(GetCredentialUnknownException(e.message))
            }
        }
    }

    // ── Registration ──────────────────────────────────────────────────────

    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>
    ) {
        if (request !is BeginCreatePublicKeyCredentialRequest) {
            callback.onError(CreateCredentialUnknownException("Unsupported credential type"))
            return
        }

        val intent = Intent(this, PasskeyActivity::class.java).apply {
            putExtra(PasskeyActivity.EXTRA_MODE, PasskeyActivity.MODE_REGISTRATION)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, REQUEST_CODE_REGISTRATION, intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val entry = CreateEntry(
            accountName = getString(R.string.app_name),
            pendingIntent = pendingIntent
        )
        callback.onResult(BeginCreateCredentialResponse(listOf(entry)))
    }

    // ── Clear state ───────────────────────────────────────────────────────

    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>
    ) {
        callback.onResult(null)
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private data class PasskeySummary(
        val entryUuid: String,
        val dbKey: String,
        val username: String,
        val rpId: String,
        val credentialIdB64url: String
    )

    // Parse rpId and optional allow-list from the WebAuthn assertion request JSON.
    private fun parseAssertionRequest(requestJson: String): Pair<String, List<String>> {
        return try {
            val json = JSONObject(requestJson)
            val rpId = json.optString("rpId", "")
            val allowCredentials = json.optJSONArray("allowCredentials")
            val allowIds = buildList {
                if (allowCredentials != null) {
                    for (i in 0 until allowCredentials.length()) {
                        val id = allowCredentials.getJSONObject(i).optString("id", "")
                        if (id.isNotEmpty()) add(id)
                    }
                }
            }
            Pair(rpId, allowIds)
        } catch (e: Exception) {
            Log.e(TAG, "parseAssertionRequest error", e)
            Pair("", emptyList())
        }
    }

    // Call the Rust FFI to find matching passkeys for the given rpId.
    // Returns an empty list if no database is open or an error occurs.
    private fun findMatchingPasskeys(rpId: String, allowIds: List<String>): List<PasskeySummary> {
        val dbKey = PasskeyRequestStore.currentDbKey ?: return emptyList()
        return try {
            val idsJson = if (allowIds.isEmpty()) "null"
            else allowIds.joinToString(",", "[", "]") { "\"$it\"" }
            val args = """{"db_key":"${dbKey.escapeJson()}","rp_id":"${rpId.escapeJson()}","allow_credential_ids":$idsJson}"""
            val result = DbServiceAPI.androidInvokeCommand("passkey_find_matching", args)
            parsePasskeySummaries(result, dbKey)
        } catch (e: Exception) {
            Log.e(TAG, "findMatchingPasskeys error", e)
            emptyList()
        }
    }

    // Parse the InvokeResult JSON returned by passkey_find_matching.
    private fun parsePasskeySummaries(resultJson: String, dbKey: String): List<PasskeySummary> {
        return try {
            val root = JSONObject(resultJson)
            val okArray = root.optJSONArray("ok") ?: return emptyList()
            buildList {
                for (i in 0 until okArray.length()) {
                    val item = okArray.getJSONObject(i)
                    add(
                        PasskeySummary(
                            entryUuid = item.getString("entry_uuid"),
                            dbKey = item.optString("db_key", dbKey),
                            username = item.optString("username", ""),
                            rpId = item.optString("rp_id", ""),
                            credentialIdB64url = item.optString("credential_id_b64url", "")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "parsePasskeySummaries error", e)
            emptyList()
        }
    }

    // Build a PublicKeyCredentialEntry for a known passkey with a specific entry UUID.
    private fun buildAssertionEntry(
        entryUuid: String,
        dbKey: String,
        username: String,
        option: BeginGetPublicKeyCredentialOption
    ): PublicKeyCredentialEntry {
        val intent = Intent(this, PasskeyActivity::class.java).apply {
            putExtra(PasskeyActivity.EXTRA_MODE, PasskeyActivity.MODE_ASSERTION)
            putExtra(PasskeyActivity.EXTRA_ENTRY_UUID, entryUuid)
            putExtra(PasskeyActivity.EXTRA_DB_KEY, dbKey)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            entryUuid.hashCode(),
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return PublicKeyCredentialEntry(
            context = this,
            username = username.ifEmpty { getString(R.string.app_name) },
            pendingIntent = pendingIntent,
            beginGetPublicKeyCredentialOption = option
        )
    }

    // Build a generic entry when the DB is locked or no passkeys are found.
    // The activity will handle unlock + passkey selection.
    private fun buildGenericAssertionEntry(
        option: BeginGetPublicKeyCredentialOption
    ): PublicKeyCredentialEntry {
        val intent = Intent(this, PasskeyActivity::class.java).apply {
            putExtra(PasskeyActivity.EXTRA_MODE, PasskeyActivity.MODE_ASSERTION)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_ASSERTION_GENERIC,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return PublicKeyCredentialEntry(
            context = this,
            username = getString(R.string.app_name),
            pendingIntent = pendingIntent,
            beginGetPublicKeyCredentialOption = option
        )
    }

    companion object {
        //private const val TAG = "OkpPasskey PasskeyProviderService"
        private const val TAG = "OkpPasskey"
        private const val REQUEST_CODE_REGISTRATION = 1001
        private const val REQUEST_CODE_ASSERTION_GENERIC = 1002
    }
}

// Simple JSON string escaping for building argument JSON manually.
private fun String.escapeJson(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")
