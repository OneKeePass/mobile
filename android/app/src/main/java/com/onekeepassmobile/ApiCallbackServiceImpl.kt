package com.onekeepassmobile

import android.util.Log
import com.facebook.react.bridge.ReactApplicationContext
import com.onekeepassmobile.autofill.OkpFillResponseBuilder
import onekeepass.mobile.ffi.AndroidApiService
import onekeepass.mobile.ffi.AutoFillDbData
import onekeepass.mobile.ffi.ClipDataArg

class ApiCallbackServiceImpl(val reactContext: ReactApplicationContext):AndroidApiService {

    companion object {
        val TAG = "ApiCallbackService"
    }

    override fun clipboardCopyString(clipData: ClipDataArg) {
        Log.d(TAG,"clipboardCopyString is called with $clipData")
    }

    override fun autofillClientAppUrlInfo(): Map<String, String> {

        val uri = OkpFillResponseBuilder.callingAppUri()

        return if (uri != null) {
            Log.d(TAG,"Returning the AF uri $uri to rust side")
            mapOf("uri" to uri!!)
        } else {
            Log.d(TAG,"Returning the empty AF uri map to rust side")
            mapOf()
        }
    }

    override fun completeAutofill(autoFillData: AutoFillDbData) {
        Log.d(TAG, "ApiCallbackService activity $reactContext.currentActivity and context $reactContext")
        when (autoFillData) {
            is AutoFillDbData.Login -> { OkpFillResponseBuilder.completeAutofillNew(autoFillData.username,autoFillData.password ) }
            else -> {Log.d(TAG,"Invalid autoFillData $autoFillData")}
        }
    }
}