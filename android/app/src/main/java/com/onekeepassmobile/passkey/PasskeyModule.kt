package com.onekeepassmobile.passkey

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PublicKeyCredential
import androidx.credentials.provider.PendingIntentHandler
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.onekeepassmobile.DbServiceAPI
import com.onekeepassmobile.FileUtils
import org.json.JSONObject
import java.util.concurrent.Executors

// React Native module that bridges ClojureScript passkey UI to Android Credential Manager.
//
// JS module name: "OkpPasskeyService"
//
// Exposed methods:
//   getPasskeyContext()         → JSON string with current passkey request context
//   completePasskeyAssertion(entryUuid, dbKey)   → signs + completes assertion request
//   completePasskeyRegistration(args)             → creates + stores + completes registration
//
// All Rust FFI calls run on a background executor thread to avoid blocking the UI.

class PasskeyModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private val executor = Executors.newSingleThreadExecutor()

    override fun getName() = "OkpPasskeyService"

    // ── Context retrieval ──────────────────────────────────────────────────

    // Returns the current passkey request context as a JSON string so that
    // ClojureScript can branch on mode and populate the UI.
    //
    // Assertion result:
    //   {"ok": {"mode":"assertion","rp_id":"...","client_data_hash_b64url":"...",
    //           "entry_uuid":"...|null","db_key":"...|null"}}
    //
    // Registration result:
    //   {"ok": {"mode":"registration","rp_id":"...","rp_name":"...",
    //           "user_name":"...","user_handle_b64url":"...",
    //           "client_data_hash_b64url":"..."}}
    //
    // No context available:
    //   {"ok": null}
    @ReactMethod
    fun getPasskeyContext(promise: Promise) {
        try {
            val mode = PasskeyRequestStore.currentMode
            if (mode == null) {
                promise.resolve("{\"ok\":null}")
                return
            }

            val json = JSONObject()
            json.put("mode", mode)

            when (mode) {
                PasskeyActivity.MODE_ASSERTION -> {
                    json.put("rp_id", PasskeyRequestStore.assertionRpId)
                    json.put("client_data_hash_b64url", PasskeyRequestStore.assertionClientDataHashB64url)
                    json.put("entry_uuid", PasskeyRequestStore.assertionEntryUuid ?: JSONObject.NULL)
                    json.put("db_key", PasskeyRequestStore.assertionDbKey ?: JSONObject.NULL)
                    val allowIds = PasskeyRequestStore.assertionAllowCredentialIds
                    json.put("allow_credential_ids", org.json.JSONArray(allowIds))
                }
                PasskeyActivity.MODE_REGISTRATION -> {
                    json.put("rp_id", PasskeyRequestStore.registrationRpId)
                    json.put("rp_name", PasskeyRequestStore.registrationRpName)
                    json.put("user_name", PasskeyRequestStore.registrationUserName)
                    json.put("user_handle_b64url", PasskeyRequestStore.registrationUserHandleB64url)
                    json.put("client_data_hash_b64url", PasskeyRequestStore.registrationClientDataHashB64url)
                }
            }

            promise.resolve("{\"ok\":${json}}")
        } catch (e: Exception) {
            Log.e(TAG, "getPasskeyContext error", e)
            promise.resolve("{\"ok\":null}")
        }
    }

    // ── Assertion completion ───────────────────────────────────────────────

    // Signs the WebAuthn assertion for the selected passkey entry and completes
    // the Android Credential Manager request.
    // Called from ClojureScript after the user selects a passkey and the DB is unlocked.
    @ReactMethod
    fun completePasskeyAssertion(entryUuid: String, dbKey: String, promise: Promise) {
        executor.execute {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                promise.reject(E_API_LEVEL, "Passkey requires Android 14+")
                return@execute
            }
            try {
                val clientDataHashB64url = PasskeyRequestStore.assertionClientDataHashB64url
                if (clientDataHashB64url.isEmpty()) {
                    promise.reject(E_NO_CONTEXT, "No assertion context available")
                    return@execute
                }

                // Call Rust to sign the assertion
                val args = buildAssertionArgs(dbKey, entryUuid, clientDataHashB64url)
                val resultJson = DbServiceAPI.androidInvokeCommand("passkey_complete_assertion", args)
                val assertionResult = parseOkResult(resultJson)
                    ?: run {
                        promise.reject(E_SIGN_FAILED, "passkey_complete_assertion failed: $resultJson")
                        return@execute
                    }

                // Build the WebAuthn authentication response JSON
                val authResponseJson = buildAuthenticationResponseJson(assertionResult)

                // Set the result on the Credential Manager PendingIntent and close the activity
                val activity = PasskeyActivity.getActivity()
                if (activity == null) {
                    promise.reject(E_NO_ACTIVITY, "PasskeyActivity is no longer alive")
                    return@execute
                }

                val resultIntent = Intent()
                PendingIntentHandler.setGetCredentialResponse(
                    resultIntent,
                    GetCredentialResponse(PublicKeyCredential(authResponseJson))
                )
                activity.runOnUiThread {
                    activity.setResult(Activity.RESULT_OK, resultIntent)
                    activity.finish()
                }

                promise.resolve("{\"ok\":null}")
            } catch (e: Exception) {
                Log.e(TAG, "completePasskeyAssertion error", e)
                promise.reject(E_UNKNOWN, e.message)
            }
        }
    }

    // ── Registration completion ────────────────────────────────────────────

    // Creates a passkey, stores it directly in the open KDBX, saves the database,
    // and completes the Android Credential Manager registration request.
    // Called from ClojureScript after the user picks a group/entry.
    //
    // args fields (ReadableMap):
    //   db_key          String  - open database key
    //   rp_id           String  - relying party id (from context, may be pre-filled)
    //   rp_name         String  - relying party name
    //   user_name       String
    //   user_handle_b64url String
    //   entry_uuid      String? - existing entry to attach to
    //   new_entry_name  String? - name for a new entry
    //   group_uuid      String? - existing group
    //   new_group_name  String? - name for a new group
    @ReactMethod
    fun completePasskeyRegistration(args: ReadableMap, promise: Promise) {
        executor.execute {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                promise.reject(E_API_LEVEL, "Passkey requires Android 14+")
                return@execute
            }
            try {
                val clientDataHashB64url = PasskeyRequestStore.registrationClientDataHashB64url
                if (clientDataHashB64url.isEmpty()) {
                    promise.reject(E_NO_CONTEXT, "No registration context available")
                    return@execute
                }

                val dbKey = args.getString("db_key") ?: run {
                    promise.reject(E_BAD_ARGS, "Missing db_key")
                    return@execute
                }

                // Build the Rust command args (PasskeyCompleteRegistrationArg)
                val rustArgs = buildRegistrationArgs(args, clientDataHashB64url)
                val resultJson = DbServiceAPI.androidInvokeCommand("passkey_complete_registration", rustArgs)
                val creationResult = parseOkResult(resultJson)
                    ?: run {
                        promise.reject(E_CREATE_FAILED, "passkey_complete_registration failed: $resultJson")
                        return@execute
                    }

                // Save the KDBX to disk using the same FD-based approach as DbServiceModule.saveKdbx
                saveKdbxViaPfd(dbKey)

                // Build the WebAuthn registration response JSON
                val regResponseJson = buildRegistrationResponseJson(creationResult)

                val activity = PasskeyActivity.getActivity()
                if (activity == null) {
                    promise.reject(E_NO_ACTIVITY, "PasskeyActivity is no longer alive")
                    return@execute
                }

                val resultIntent = Intent()
                PendingIntentHandler.setCreateCredentialResponse(
                    resultIntent,
                    CreatePublicKeyCredentialResponse(regResponseJson)
                )
                activity.runOnUiThread {
                    activity.setResult(Activity.RESULT_OK, resultIntent)
                    activity.finish()
                }

                promise.resolve("{\"ok\":null}")
            } catch (e: Exception) {
                Log.e(TAG, "completePasskeyRegistration error", e)
                promise.reject(E_UNKNOWN, e.message)
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private fun buildAssertionArgs(dbKey: String, entryUuid: String, clientDataHashB64url: String): String {
        return """{"db_key":"${dbKey.escapeJson()}","entry_uuid":"${entryUuid.escapeJson()}","client_data_hash_b64url":"${clientDataHashB64url.escapeJson()}"}"""
    }

    private fun buildRegistrationArgs(args: ReadableMap, clientDataHashB64url: String): String {
        fun ReadableMap.optStr(key: String) = if (hasKey(key) && !isNull(key)) "\"${getString(key)!!.escapeJson()}\"" else "null"

        return buildString {
            append("{")
            append("\"org_db_key\":\"${args.getString("db_key")!!.escapeJson()}\",")
            append("\"rp_id\":\"${(args.getString("rp_id") ?: PasskeyRequestStore.registrationRpId).escapeJson()}\",")
            append("\"rp_name\":\"${(args.getString("rp_name") ?: PasskeyRequestStore.registrationRpName).escapeJson()}\",")
            append("\"user_name\":\"${(args.getString("user_name") ?: PasskeyRequestStore.registrationUserName).escapeJson()}\",")
            append("\"user_handle_b64url\":\"${(args.getString("user_handle_b64url") ?: PasskeyRequestStore.registrationUserHandleB64url).escapeJson()}\",")
            append("\"client_data_hash_b64url\":\"${clientDataHashB64url.escapeJson()}\",")
            append("\"entry_uuid\":${args.optStr("entry_uuid")},")
            append("\"new_entry_name\":${args.optStr("new_entry_name")},")
            append("\"group_uuid\":${args.optStr("group_uuid")},")
            append("\"new_group_name\":${args.optStr("new_group_name")}")
            append("}")
        }
    }

    // Parse the {"ok": {...}} wrapper returned by androidInvokeCommand.
    // Returns the inner JSONObject on success, null on error.
    private fun parseOkResult(resultJson: String): JSONObject? {
        return try {
            val root = JSONObject(resultJson)
            if (root.has("error") && !root.isNull("error")) {
                Log.e(TAG, "Rust error: ${root.getString("error")}")
                null
            } else {
                root.optJSONObject("ok")
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseOkResult parse error for: $resultJson", e)
            null
        }
    }

    // Build the WebAuthn authentication response JSON from the Rust assertion result.
    // Fields: signature_b64url, authenticator_data_b64url, credential_id_b64url,
    //         user_handle_b64url, rp_id
    private fun buildAuthenticationResponseJson(result: JSONObject): String {
        val credentialId = result.optString("credential_id_b64url", "")
        val authenticatorData = result.optString("authenticator_data_b64url", "")
        val signature = result.optString("signature_b64url", "")
        val userHandle = result.optString("user_handle_b64url", "")
        val clientDataJson = PasskeyRequestStore.assertionClientDataJsonB64url

        return JSONObject().apply {
            put("id", credentialId)
            put("rawId", credentialId)
            put("type", "public-key")
            put("authenticatorAttachment", "platform")
            put("clientExtensionResults", JSONObject())
            put("response", JSONObject().apply {
                // Omit clientDataJSON when null (Chrome/Brave path): the browser provided
                // clientDataHash so it already has its own clientDataJSON and will substitute it.
                // Include clientDataJSON when non-null (Firefox path): we built it ourselves.
                if (clientDataJson != null) put("clientDataJSON", clientDataJson)
                put("authenticatorData", authenticatorData)
                put("signature", signature)
                put("userHandle", userHandle)
            })
        }.toString()
    }

    // Build the WebAuthn registration response JSON from the Rust creation result.
    // Fields: credential_id_b64url, attestation_object_b64url
    private fun buildRegistrationResponseJson(result: JSONObject): String {
        val credentialId = result.optString("credential_id_b64url", "")
        val attestationObject = result.optString("attestation_object_b64url", "")
        val clientDataJson = PasskeyRequestStore.registrationClientDataJsonB64url

        return JSONObject().apply {
            put("id", credentialId)
            put("rawId", credentialId)
            put("type", "public-key")
            put("clientExtensionResults", JSONObject())
            put("response", JSONObject().apply {
                if (clientDataJson != null) put("clientDataJSON", clientDataJson)
                put("attestationObject", attestationObject)
            })
        }.toString()
    }

    // Save the open database to disk using the same ContentResolver FD approach
    // as DbServiceModule.saveKdbx. The db_key IS the content URI on Android.
    private fun saveKdbxViaPfd(fullFileNameUri: String) {
        val uri = Uri.parse(fullFileNameUri)
        val contentResolver = reactApplicationContext.contentResolver
        val fileName = FileUtils.getMetaInfo(contentResolver, uri)?.filename ?: ""
        val fd = contentResolver.openFileDescriptor(uri, "rwt") ?: run {
            Log.w(TAG, "saveKdbxViaPfd: could not open FD for $fullFileNameUri")
            return
        }
        try {
            when (val response = DbServiceAPI.saveKdbx(
                fd.fd.toULong(),
                fullFileNameUri,
                fileName,
                true // overwrite = true; no conflict check needed during passkey registration
            )) {
                is onekeepass.mobile.ffi.ApiResponse.Success ->
                    Log.d(TAG, "saveKdbxViaPfd success")
                is onekeepass.mobile.ffi.ApiResponse.Failure ->
                    Log.e(TAG, "saveKdbxViaPfd Rust error: ${response.result}")
            }
        } finally {
            fd.close()
        }
    }

    companion object {
        private const val TAG = "OkpPasskey PasskeyModule"
        private const val E_API_LEVEL = "E_API_LEVEL"
        private const val E_NO_CONTEXT = "E_NO_CONTEXT"
        private const val E_NO_ACTIVITY = "E_NO_ACTIVITY"
        private const val E_SIGN_FAILED = "E_SIGN_FAILED"
        private const val E_CREATE_FAILED = "E_CREATE_FAILED"
        private const val E_BAD_ARGS = "E_BAD_ARGS"
        private const val E_UNKNOWN = "E_UNKNOWN"
    }
}

private fun String.escapeJson(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")
