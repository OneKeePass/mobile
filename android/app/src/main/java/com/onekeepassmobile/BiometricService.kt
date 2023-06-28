package com.onekeepassmobile

import android.util.Log
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import java.util.concurrent.Executor


class BiometricService(val reactContext: ReactApplicationContext) {
    private val TAG = "BiometricService";
    private val biometricManager = BiometricManager.from(reactContext)

    fun biometricAuthenticationAvailbale(): Boolean {
        when (biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                Log.d(TAG, "App can authenticate using biometrics.")
                return true
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                Log.e(TAG, "No biometric features available on this device.")
                return false
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                Log.e(TAG, "Biometric features are currently unavailable.")
                return false
            }

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Log.e(TAG, "User has not yet enrolled to use Biometric features")
                return false
            }
            else -> {
                Log.e(TAG, "biometricManager.canAuthenticate call failed")
                return false
            }
        }
    }

    fun showPrompt(activity: FragmentActivity, executor: Executor, promise: Promise) {
        val biometricPrompt = BiometricPrompt(activity, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int,
                                                       errString: CharSequence) {
                        Log.e(TAG, "Error code: " + errorCode + " error String: " + errString)
                        promise.resolve(DbServiceAPI.jsonService().okJsonString("AuthenticationCancelled"))
                        // promise.resolve(DbServiceAPI.jsonService().mapAsOkJsonString(hashMapOf("AuthenticationCancelled" to "true")))
                        //super.onAuthenticationError(errorCode, errString)
                    }

                    override fun onAuthenticationSucceeded(
                            result: BiometricPrompt.AuthenticationResult) {
                        Log.i(TAG, "onAuthenticationSucceeded")
                        // super.onAuthenticationSucceeded(result)
                        promise.resolve(DbServiceAPI.jsonService().okJsonString("AuthenticationSucceeded"))
                        //promise.resolve(DbServiceAPI.jsonService().mapAsOkJsonString(hashMapOf("AuthenticationSucceeded" to "true")))
                    }

                    override fun onAuthenticationFailed() {
                        Log.i(TAG, "onAuthenticationFailed")
                        // super.onAuthenticationFailed()
                        promise.resolve(DbServiceAPI.jsonService().okJsonString("AuthenticationFailed"))
                    }

                })

        // For now, these texts are hard coded here.
        // TODO: Pass these texts from UI layer so that we can enable supporting localization
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock the database")
                .setSubtitle("Log in using your biometric credential")
                .setNegativeButtonText("Cancel")
                .build()

        biometricPrompt.authenticate(promptInfo)
    }

    /*
    private fun getAuthenticationCallback(): BiometricPrompt.AuthenticationCallback? {
        // Callback for biometric authentication result
        return object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                Log.e(TAG, "Error code: " + errorCode + "error String: " + errString)
                super.onAuthenticationError(errorCode, errString)
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                Log.i(TAG, "onAuthenticationSucceeded")
                super.onAuthenticationSucceeded(result)
            }

            override fun onAuthenticationFailed() {
                Log.i(TAG, "onAuthenticationFailed")
                super.onAuthenticationFailed()
            }
        }
    }
    */
}