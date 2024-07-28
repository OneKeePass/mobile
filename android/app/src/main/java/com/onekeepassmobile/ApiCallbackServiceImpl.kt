package com.onekeepassmobile

import android.util.Log
import com.onekeepassmobile.autofill.OkpFillResponseBuilder
import onekeepass.mobile.ffi.AndroidApiService
import onekeepass.mobile.ffi.AppClipboardCopyData
import onekeepass.mobile.ffi.AutoFillDbData
import onekeepass.mobile.ffi.CommonDeviceServiceEx

// ApiCallbackServiceImpl is created during DbServiceAPI.initialize call
// As this singleton may be created by MainActivity or by AutofillAuthenticationActivity,
// we should not pass 'ReactApplicationContext' while creating the instance like done for
// other services
// If we need to access 'ReactApplicationContext', then we need to store some activity specfic one
// and access accordingly or we can use MainApplication object methods to get context

// Provides apis that are called by rust side backend
class ApiCallbackServiceImpl():AndroidApiService,CommonDeviceServiceEx {

    companion object {
        val TAG = "ApiCallbackService"
    }

    override fun clipboardCopyString(clipData: AppClipboardCopyData) {
        Log.d(TAG,"clipboardCopyString is called with $clipData and called OkpClipboardManager.setText")
        OkpClipboardManager.setText(clipData )
    }

    // Gets the uri of an app that has requested autofill
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

    // Called (from rust side) when user selects an entry's Login credentials
    override fun completeAutofill(autoFillData: AutoFillDbData) {
        when (autoFillData) {
            is AutoFillDbData.Login -> { OkpFillResponseBuilder.completeLoginAutofill(autoFillData.username,autoFillData.password ) }
            else -> {Log.d(TAG,"Invalid autoFillData $autoFillData")}
        }
    }
}