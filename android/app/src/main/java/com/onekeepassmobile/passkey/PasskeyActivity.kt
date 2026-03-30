package com.onekeepassmobile.passkey

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.credentials.provider.PendingIntentHandler
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate

// React Native activity that hosts the passkey UI for both assertion (sign-in)
// and registration (creation) flows. Launched via PendingIntent by
// PasskeyProviderService in response to an Android Credential Manager request.
//
// Initial props passed to React Native:
//   androidPasskeyMode: "assertion" | "registration"
//
// The ClojureScript code reads the passkey context from PasskeyModule.getPasskeyContext()
// and calls PasskeyModule.completePasskeyAssertion / completePasskeyRegistration when done.

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class PasskeyActivity : ReactActivity() {

    override fun getMainComponentName(): String = "OneKeePassMobile"

    override fun createReactActivityDelegate(): ReactActivityDelegate {
        return object : DefaultReactActivityDelegate(this, mainComponentName!!, fabricEnabled) {
            override fun getLaunchOptions(): Bundle {
                val mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_ASSERTION
                return Bundle().apply {
                    putString("androidPasskeyMode", mode)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val mode = intent?.getStringExtra(EXTRA_MODE)
        Log.d(TAG, "onCreate mode=$mode")
        passkeyActivity = this

        // Extract passkey context from the Android Credential Manager PendingIntent
        // and store it in PasskeyRequestStore for PasskeyModule to read.
        when (mode) {
            MODE_ASSERTION -> {
                val request = PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)
                if (request != null) {
                    // Find the GetPublicKeyCredentialOption in the request
                    val option = request.credentialOptions
                        .filterIsInstance<androidx.credentials.GetPublicKeyCredentialOption>()
                        .firstOrNull()
                    if (option != null) {
                        PasskeyRequestStore.storeAssertionContext(
                            entryUuid = intent.getStringExtra(EXTRA_ENTRY_UUID),
                            dbKey = intent.getStringExtra(EXTRA_DB_KEY),
                            requestJson = option.requestJson,
                            providedClientDataHash = option.clientDataHash
                        )
                    } else {
                        Log.w(TAG, "No GetPublicKeyCredentialOption found in assertion request")
                    }
                } else {
                    Log.w(TAG, "No ProviderGetCredentialRequest found in intent")
                }
            }
            MODE_REGISTRATION -> {
                val request = PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
                if (request != null) {
                    val createOption = request.callingRequest
                        as? androidx.credentials.CreatePublicKeyCredentialRequest
                    if (createOption != null) {
                        PasskeyRequestStore.storeRegistrationContext(
                            requestJson = createOption.requestJson,
                            providedClientDataHash = createOption.clientDataHash
                        )
                    } else {
                        Log.w(TAG, "No CreatePublicKeyCredentialRequest found in creation request")
                    }
                } else {
                    Log.w(TAG, "No ProviderCreateCredentialRequest found in intent")
                }
            }
            else -> Log.w(TAG, "Unknown passkey mode: $mode")
        }

        super.onCreate(savedInstanceState)
    }

    override fun onDestroy() {
        super.onDestroy()
        passkeyActivity = null
        PasskeyRequestStore.clear()
    }

    companion object {
        //private const val TAG = "OkpPasskey PasskeyActivity"
        private const val TAG = "OkpPasskey"
        const val EXTRA_MODE = "passkey_mode"
        const val EXTRA_ENTRY_UUID = "entry_uuid"
        const val EXTRA_DB_KEY = "db_key"
        const val MODE_ASSERTION = "assertion"
        const val MODE_REGISTRATION = "registration"

        private var passkeyActivity: PasskeyActivity? = null

        fun getActivity(): PasskeyActivity? = passkeyActivity
    }
}
