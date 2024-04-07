package com.onekeepassmobile

import android.content.Intent
import android.util.Log
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter

private const val TAG = "EventEmitter"

object EventEmitter {

    private lateinit var reactApplicationContext: ReactApplicationContext
    var intentOfOnCreate: Intent? = null


    private const val EVENT_ON_TIME_TICK = "onTimerTick"
    private const val  EVENT_ENTRY_OTP_UPDATE = "onEntryOtpUpdate"


    fun initialize(reactContext: ReactApplicationContext) {
        reactApplicationContext = reactContext
    }

    /**
     * Gets the uri of a valid .kdbx file pressed by user
     */
    fun kdbxUriToOpenOnCreate(): String {
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


    fun emitOtpUpdate(jsonString: String) {
        reactApplicationContext.getJSModule(RCTDeviceEventEmitter::class.java)
                .emit(EVENT_ENTRY_OTP_UPDATE, jsonString)
    }

    fun emitTickUpdate(jsonString: String) {
        reactApplicationContext.getJSModule(RCTDeviceEventEmitter::class.java)
                .emit(EVENT_ON_TIME_TICK, jsonString)
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
        Log.d(TAG, "In EventEmitter onCreateIntent Received intent $intent with ${intent.action}")

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