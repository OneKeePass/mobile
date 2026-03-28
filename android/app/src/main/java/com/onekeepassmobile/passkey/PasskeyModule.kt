package com.onekeepassmobile.passkey

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PublicKeyCredential
import androidx.credentials.provider.PendingIntentHandler
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.onekeepassmobile.DbServiceAPI
import com.onekeepassmobile.FileUtils
import com.onekeepassmobile.MainApplication
import org.json.JSONObject

// React Native module that bridges ClojureScript passkey UI to Android Credential Manager.
//
// JS module name: "OkpPasskeyService"
//
// Exposed method:
//   getPasskeyContext() → JSON string with current passkey request context
//
// Passkey crypto (signing, key creation), WebAuthn JSON building, and Credential Manager
// completion are all handled by the Rust FFI layer via android-invoke-api
// "passkey_complete_assertion" / "passkey_complete_registration". Rust calls back into
// Kotlin via the AndroidApiService uniffi callback (ApiCallbackServiceImpl), which
// delegates to PasskeyModule.completePasskeyAssertion / completePasskeyRegistration
// (companion object functions).

class PasskeyModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName() = "OkpPasskeyService"

    // ── Context retrieval ──────────────────────────────────────────────────

    // Returns the current passkey request context as a JSON string so that
    // ClojureScript can branch on mode and populate the UI.
    //
    // Assertion result:
    //   {"ok": {"mode":"assertion","rp_id":"...","client_data_hash_b64url":"...",
    //           "client_data_json_b64url":"...|null","allow_credential_ids":[...],
    //           "entry_uuid":"...|null","db_key":"...|null"}}
    //
    // Registration result:
    //   {"ok": {"mode":"registration","rp_id":"...","rp_name":"...",
    //           "user_name":"...","user_handle_b64url":"...",
    //           "client_data_hash_b64url":"...","client_data_json_b64url":"...|null"}}
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
                    json.put("client_data_json_b64url", PasskeyRequestStore.assertionClientDataJsonB64url ?: JSONObject.NULL)
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
                    json.put("client_data_json_b64url", PasskeyRequestStore.registrationClientDataJsonB64url ?: JSONObject.NULL)
                }
            }

            promise.resolve("{\"ok\":${json}}")
        } catch (e: Exception) {
            Log.e(TAG, "getPasskeyContext error", e)
            promise.resolve("{\"ok\":null}")
        }
    }

    companion object {
        private const val TAG = "OkpPasskey PasskeyModule"

        // ── Passkey completion (called from ApiCallbackServiceImpl via Rust callback) ──

        // Called by ApiCallbackServiceImpl.completePasskeyAssertion after the Rust FFI
        // signs the assertion and builds the AuthenticationResponseJSON. Finishes PasskeyActivity.
        fun completePasskeyAssertion(responseJson: String) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Log.e(TAG, "completePasskeyAssertion: requires Android 14+")
                return
            }
            val activity = PasskeyActivity.getActivity() ?: run {
                Log.e(TAG, "completePasskeyAssertion: PasskeyActivity is no longer alive")
                return
            }
            val resultIntent = Intent()
            PendingIntentHandler.setGetCredentialResponse(
                resultIntent,
                GetCredentialResponse(PublicKeyCredential(responseJson))
            )
            activity.runOnUiThread {
                activity.setResult(Activity.RESULT_OK, resultIntent)
                activity.finish()
            }
        }

        // Called by ApiCallbackServiceImpl.completePasskeyRegistration after the Rust FFI
        // creates the keypair and builds the RegistrationResponseJSON. Saves the KDBX to
        // disk and finishes PasskeyActivity.
        fun completePasskeyRegistration(responseJson: String, orgDbKey: String) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Log.e(TAG, "completePasskeyRegistration: requires Android 14+")
                return
            }
            saveKdbxViaPfd(orgDbKey)
            val activity = PasskeyActivity.getActivity() ?: run {
                Log.e(TAG, "completePasskeyRegistration: PasskeyActivity is no longer alive")
                return
            }
            val resultIntent = Intent()
            PendingIntentHandler.setCreateCredentialResponse(
                resultIntent,
                CreatePublicKeyCredentialResponse(responseJson)
            )
            activity.runOnUiThread {
                activity.setResult(Activity.RESULT_OK, resultIntent)
                activity.finish()
            }
        }

        // Saves the open database to disk using the ContentResolver FD approach.
        // Uses MainApplication.getInstanceContext() since this is a companion object
        // without access to reactApplicationContext.
        private fun saveKdbxViaPfd(fullFileNameUri: String) {
            val context = MainApplication.getInstanceContext()
            val uri = Uri.parse(fullFileNameUri)
            val contentResolver = context.contentResolver
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
    }
}
