package com.onekeepassmobile

import android.content.Intent
import android.net.Uri
import android.util.Log
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter

private const val TAG = "EventEmitter"

object EventEmitter {

    private lateinit var reactApplicationContext: ReactApplicationContext
    var intentOfOnCreate: Intent? = null

    // See onekeepass/mobile/events/native_events.cljs how these events are received and handled
    private const val EVENT_ON_TIME_TICK = "onTimerTick"
    private const val EVENT_ENTRY_OTP_UPDATE = "onEntryOtpUpdate"
    private const val EVENT_APP_BECOMES_ACTIVE = "onAppBecomingActive"
    private const val EVENT_APP_BECOMES_INACTIVE = "onAppBecomingInActive"
    private const val EVENT_ON_OTP_AUTH_URL = "onOtpAuthUrl"

    private const val OTP_AUTH_SCHEME = "otpauth"


    fun initialize(reactContext: ReactApplicationContext) {
        reactApplicationContext = reactContext
    }

    /**
     * Returns the uri of a VIEW intent only when it is an 'otpauth://' one - the url the user
     * gets by pressing a scanned 2FA QR code in the device Camera app or an otpauth link
     * anywhere else. Returns null for all other intents including the .kdbx file ones.
     *
     * IMPORTANT: The url holds the TOTP shared secret in plain text and is never logged
     */
    private fun otpAuthUri(intent: Intent?): Uri? {
        val uri = intent?.data ?: return null
        return if (intent.action == Intent.ACTION_VIEW && uri.scheme == OTP_AUTH_SCHEME) uri else null
    }

    private fun otpAuthUrlJson(uri: Uri): String {
        return DbServiceAPI.jsonService().mapAsOkJsonString(hashMapOf("otp_url" to uri.toString()))
    }

    /**
     * Gets the uri of a valid .kdbx file pressed by user
     */
    fun kdbxUriToOpenOnCreate(): String {
        // 'intentOfOnCreate' is a single slot shared with 'otpAuthUrlOnCreate' and the UI layer
        // calls both. An otpauth intent belongs to that call and is left here as it is
        if (otpAuthUri(intentOfOnCreate) != null) {
            return "{}"
        }

        var uri = intentOfOnCreate?.action?.let {
            if (it == "android.intent.action.VIEW") {
                var uri = intentOfOnCreate?.data
                Log.d(TAG, "EventEmitter onApplicationOpenURL Uri received ${uri}")
                uri
            } else {
                null
            }
        }
        // Need to set to null so that we can return "{}"
        // if  DbServiceModule.kdbxUriToOpenOnCreate is called more than once from UI layer.
        // This ensures that opening this uri happens once in UI layer
        intentOfOnCreate = null
        if (uri != null) {
            return DbServiceAPI.formJsonWithFileName(uri.toString())
        } else {
            return "{}"
        }
    }

    /**
     * Gets any 'otpauth://' url that started the app. Same pull concept as
     * 'kdbxUriToOpenOnCreate' - the ReactApplicationContext is not yet ready in onCreate and
     * an emitted event would reach no listener
     */
    fun otpAuthUrlOnCreate(): String {
        val uri = otpAuthUri(intentOfOnCreate) ?: return "{}"

        Log.d(TAG, "An otpauth url is received in the onCreate intent")

        // Set to null so that the url is used only once in the UI layer
        intentOfOnCreate = null

        return otpAuthUrlJson(uri)
    }

    fun emitOtpUpdate(jsonString: String) {
        reactApplicationContext.getJSModule(RCTDeviceEventEmitter::class.java)
                .emit(EVENT_ENTRY_OTP_UPDATE, jsonString)
    }

    fun emitTickUpdate(jsonString: String) {
        reactApplicationContext.getJSModule(RCTDeviceEventEmitter::class.java)
                .emit(EVENT_ON_TIME_TICK, jsonString)
    }

    fun emitAppBecomesActive() {
        // 'reactApplicationContext' is initialized in DbServiceModule's init block which RN
        // constructs asynchronously. The very first MainActivity.onResume during a cold start
        // can fire before that, so we guard against the lateinit not being set yet.
        // A missed event here is harmless as no database is open at that point.
        if (!::reactApplicationContext.isInitialized) {
            return
        }
        reactApplicationContext.getJSModule(RCTDeviceEventEmitter::class.java)
            .emit(EVENT_APP_BECOMES_ACTIVE, "{}")
    }

    fun emitAppBecomesInactive() {
        if (!::reactApplicationContext.isInitialized) {
            return
        }
        reactApplicationContext.getJSModule(RCTDeviceEventEmitter::class.java)
            .emit(EVENT_APP_BECOMES_INACTIVE, "{}")
    }

    // This does not work as the registration of a listener for this event in UI is done only after this call
    fun emitKdbxUriToOpenEvent(reactContext: ReactApplicationContext) {
        reactApplicationContext = reactContext
        intentOfOnCreate?.action?.let {
            if (it == "android.intent.action.VIEW") {
                var uri = intentOfOnCreate?.data
                Log.d(TAG, "EventEmitter onCreate Uri received ${uri}")
                if (uri != null) {
                    reactApplicationContext.getJSModule(RCTDeviceEventEmitter::class.java)
                            .emit("onApplicationOpenURL", DbServiceAPI.formJsonWithFileName(uri.toString()))
                    Log.d(TAG, "EventEmitter onCreateIntent Uri received ${uri} sent by emitting....")
                }
            }
        }
        intentOfOnCreate = null
    }

    // Called from MainActivity.onCreate
    // The app is started if it is not running when user presses kdbx file
    // with the extension .kdbx and then this intent will have
    // the uri the user pressed and is used to show the open database dialog.
    fun onCreateIntent(intent: Intent) {
        if (otpAuthUri(intent) != null) {
            Log.d(TAG, "In EventEmitter onCreateIntent Received an otpauth intent")
        } else {
            Log.d(TAG, "In EventEmitter onCreateIntent Received intent $intent with ${intent.action}")
        }

        // Initially tried to use 'emitKdbxUriToOpenEvent'
        // But we can't call emit here as ReactApplicationContext may not be ready yet
        // So the following pull uri concept is used  

        // We set the intent first here and use later it later in kdbxUriToOpenOnCreate
        // The JS side calls DbServiceModule.kdbxUriToOpenOnCreate and gets any Uri to open 
        intentOfOnCreate = intent
    }

    // Called when user presses kdbx file with the extension .kdbx and the app
    // is not on the top. The app is brought to the front and this func is called from MainActivity.onNewIntent
    fun onNewIntent(intent: Intent) {
        val otpUri = otpAuthUri(intent)
        if (otpUri != null) {
            Log.d(TAG, "In EventEmitter onNewIntent Received an otpauth intent")
            if (::reactApplicationContext.isInitialized) {
                reactApplicationContext.getJSModule(RCTDeviceEventEmitter::class.java)
                        .emit(EVENT_ON_OTP_AUTH_URL, otpAuthUrlJson(otpUri))
            } else {
                // The UI layer is not yet listening. Stashing the intent lets the pull call
                // 'otpAuthUrlOnCreate' pick it up instead of the url getting lost
                intentOfOnCreate = intent
            }
            return
        }

        Log.d(TAG, "In EventEmitter onNewIntent Received intent $intent with ${intent.action}")
        intent.action?.let {
            if (it == "android.intent.action.VIEW") {
                var uri = intent?.data
                Log.d(TAG, "EventEmitter onNewIntent Uri received ${uri}")
                if (uri != null) {
                    reactApplicationContext.getJSModule(RCTDeviceEventEmitter::class.java)
                            .emit("onApplicationOpenURL", DbServiceAPI.formJsonWithFileName(uri.toString()))
                    Log.d(TAG, "EventEmitter onNewIntent Uri received ${uri} sent by emitting....")
                }
            }
        }
    }
}