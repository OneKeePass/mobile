package com.onekeepassmobile

import android.util.Log
import android.view.View
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ReactShadowNode
import com.facebook.react.uimanager.ViewManager

class OneKeePassAppPackage : ReactPackage {

    override fun createViewManagers(
            reactContext: ReactApplicationContext
    ): MutableList<ViewManager<View, ReactShadowNode<*>>> {
        Log.d(TAG,"createViewManagers is called with context $reactContext")
        return mutableListOf()
    }

    override fun createNativeModules(
            reactContext: ReactApplicationContext
    ): MutableList<NativeModule>  {
        Log.d(TAG,"createNativeModules is called with context $reactContext")
        return listOf(
                DbServiceModule(reactContext) ,
                DocumentPickerServiceModule(reactContext),
                ExportServiceModule(reactContext),
        ).toMutableList()
    }

    companion object {
        private val TAG = "OneKeePassAppPackage"
    }
}