package com.onekeepassmobile.passkey

import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

// Shared singleton holding the in-flight passkey request context.
// Set by PasskeyActivity.onCreate and read by PasskeyModule.
object PasskeyRequestStore {

    //private const val TAG = "OkpPasskey PasskeyRequestStore"
    private const val TAG = "OkpPasskey"
    // ── Mode ──────────────────────────────────────────────────────────────

    var currentMode: String? = null

    // ── Assertion context ─────────────────────────────────────────────────

    // UUID of the selected passkey entry (populated in assertion mode when
    // the service already knows which entry the user selected)
    var assertionEntryUuid: String? = null

    // DB key of the open database containing the passkey
    var assertionDbKey: String? = null

    // RP ID extracted from the request JSON
    var assertionRpId: String = ""

    // base64url credential IDs from allowCredentials (empty = no filtering)
    var assertionAllowCredentialIds: List<String> = emptyList()

    // base64url-encoded SHA-256(clientDataJSON) passed to Rust for signing
    var assertionClientDataHashB64url: String = ""

    // base64url-encoded clientDataJSON to include in the assertion response
    // (null when the app provided its own clientDataHash)
    var assertionClientDataJsonB64url: String? = null

    // ── Registration context ──────────────────────────────────────────────

    var registrationRpId: String = ""
    var registrationRpName: String = ""
    var registrationUserName: String = ""
    var registrationUserHandleB64url: String = ""
    var registrationClientDataHashB64url: String = ""
    var registrationClientDataJsonB64url: String? = null

    // COSE algorithm selected from pubKeyCredParams: -7 (ES256), -8 (EdDSA), -257 (RS256).
    var registrationAlgorithm: Int = -7

    // Set by completePasskeyRegistration if saveKdbxViaPfd fails.
    // Read and cleared by getRegistrationSaveError @ReactMethod.
    var registrationSaveError: String? = null

    var registrationResponseJson: String = ""

    // ── DB key tracking ───────────────────────────────────────────────────

    // Updated by DbServiceModule when the main app successfully opens a database
    @Volatile
    var currentDbKey: String? = null

    // ── Helpers ───────────────────────────────────────────────────────────

    // Called from PasskeyActivity when the system provides a passkey assertion
    // request via PendingIntentHandler. Extracts challenge and rpId from the
    // request JSON, builds clientDataJSON, and hashes it.
    fun storeAssertionContext(
        entryUuid: String?,
        dbKey: String?,
        requestJson: String,
        providedClientDataHash: ByteArray?
    ) {
        currentMode = PasskeyActivity.MODE_ASSERTION
        assertionEntryUuid = entryUuid
        assertionDbKey = dbKey

        try {
            val json = JSONObject(requestJson)
            val challenge = json.optString("challenge", "")
            assertionRpId = json.optString("rpId", "")

            val allowCredentials = json.optJSONArray("allowCredentials")
            assertionAllowCredentialIds = buildList {
                if (allowCredentials != null) {
                    for (i in 0 until allowCredentials.length()) {
                        val id = allowCredentials.getJSONObject(i).optString("id", "")
                        if (id.isNotEmpty()) add(id)
                    }
                }
            }

            if (providedClientDataHash != null) {
                // App already computed the hash; use it directly for signing.
                // Do NOT build clientDataJSON here — the caller (e.g. Chrome) has its own
                // clientDataJSON whose origin is "android:apk-key-hash:..." (not "https://<rpId>").
                // Chrome validates SHA-256(returned_clientDataJSON) == clientDataHash, so returning
                // our own JSON with the wrong origin causes Chrome to fail with an unknown error.
                // Leave assertionClientDataJsonB64url = null; Chrome supplies its own clientDataJSON.
                assertionClientDataHashB64url = Base64.encodeToString(
                    providedClientDataHash, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                )
                assertionClientDataJsonB64url = null
            } else {
                // Build clientDataJSON per WebAuthn spec and hash it
                val clientDataJson = buildClientDataJson(
                    type = "webauthn.get",
                    challenge = challenge,
                    rpId = assertionRpId
                )
                val hash = sha256(clientDataJson)
                assertionClientDataHashB64url = Base64.encodeToString(
                    hash, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                )
                assertionClientDataJsonB64url = Base64.encodeToString(
                    clientDataJson, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "storeAssertionContext parse error", e)
        }
    }

    // Called from PasskeyActivity when the system provides a passkey registration
    // request via PendingIntentHandler.
    fun storeRegistrationContext(
        requestJson: String,
        providedClientDataHash: ByteArray?
    ) {
        currentMode = PasskeyActivity.MODE_REGISTRATION

        try {
            val json = JSONObject(requestJson)
            val challenge = json.optString("challenge", "")
            val rp = json.optJSONObject("rp")
            val user = json.optJSONObject("user")

            registrationRpId = rp?.optString("id", "") ?: ""
            registrationRpName = rp?.optString("name", registrationRpId) ?: registrationRpId
            registrationUserName = user?.optString("name", "") ?: ""
            registrationUserHandleB64url = user?.optString("id", "") ?: ""
            registrationAlgorithm = selectAlgorithm(json.optJSONArray("pubKeyCredParams"))

            if (providedClientDataHash != null) {
                registrationClientDataHashB64url = Base64.encodeToString(
                    providedClientDataHash, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                )
                // Try to reconstruct clientDataJSON so Chrome can validate
                // sha256(returned_clientDataJSON) == clientDataHash.
                // Chrome's RegistrationResponseJSON requires clientDataJSON to be present;
                // without it Chrome shows "unknown error" even though assertion (which Chrome
                // assembles itself from our signature + auth data) works fine without it.
                val candidate = buildClientDataJson(
                    type = "webauthn.create",
                    challenge = challenge,
                    rpId = registrationRpId
                )
                if (sha256(candidate).contentEquals(providedClientDataHash)) {
                    registrationClientDataJsonB64url = Base64.encodeToString(
                        candidate, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                    )
                } else {
                    Log.w(TAG, "storeRegistrationContext: reconstructed clientDataJSON hash " +
                          "does not match providedClientDataHash; leaving it null")
                    registrationClientDataJsonB64url = null
                }
            } else {
                val clientDataJson = buildClientDataJson(
                    type = "webauthn.create",
                    challenge = challenge,
                    rpId = registrationRpId
                )
                val hash = sha256(clientDataJson)
                registrationClientDataHashB64url = Base64.encodeToString(
                    hash, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                )
                registrationClientDataJsonB64url = Base64.encodeToString(
                    clientDataJson, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "storeRegistrationContext parse error", e)
        }
    }

    fun clear() {
        currentMode = null
        assertionEntryUuid = null
        assertionDbKey = null
        assertionRpId = ""
        assertionAllowCredentialIds = emptyList()
        assertionClientDataHashB64url = ""
        assertionClientDataJsonB64url = null
        registrationRpId = ""
        registrationRpName = ""
        registrationUserName = ""
        registrationUserHandleB64url = ""
        registrationClientDataHashB64url = ""
        registrationClientDataJsonB64url = null
        registrationSaveError = null
        registrationResponseJson = ""
        registrationAlgorithm = -7
    }

    // ── Private helpers ───────────────────────────────────────────────────

    // Picks the first COSE algorithm in the RP's preferred order that we support.
    // Falls back to -7 (ES256/P-256) if none match.
    private fun selectAlgorithm(params: JSONArray?): Int {
        if (params == null) return -7
        val supported = setOf(-7, -8, -257)
        for (i in 0 until params.length()) {
            val alg = params.optJSONObject(i)?.optInt("alg", 0) ?: 0
            if (alg in supported) return alg
        }
        return -7
    }

    private fun buildClientDataJson(type: String, challenge: String, rpId: String): ByteArray {
        return JSONObject().apply {
            put("type", type)
            put("challenge", challenge)
            put("origin", "https://$rpId")
            put("crossOrigin", false)
        }.toString().toByteArray(Charsets.UTF_8)
    }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)
}
