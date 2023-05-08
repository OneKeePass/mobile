package com.onekeepassmobile

import android.view.View
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ReactShadowNode
import com.facebook.react.uimanager.ViewManager

class OneKeePassAppPackage : ReactPackage {

    override fun createViewManagers(
            reactContext: ReactApplicationContext
    ): MutableList<ViewManager<View, ReactShadowNode<*>>> = mutableListOf()

    override fun createNativeModules(
            reactContext: ReactApplicationContext
    ): MutableList<NativeModule> = listOf(
            DbServiceModule(reactContext) ,
            DocumentPickerServiceModule(reactContext),
            ExportServiceModule(reactContext),
            ).toMutableList()
}