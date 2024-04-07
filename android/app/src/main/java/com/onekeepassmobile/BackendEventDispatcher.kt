package com.onekeepassmobile

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.modules.core.DeviceEventManagerModule
import onekeepass.mobile.ffi.EventDispatch

class BackendEventDispatcher(val reactContext: ReactApplicationContext) : EventDispatch {
    override fun sendOtpUpdate(jsonString: String) {
        //TODO("Not yet implemented")

        EventEmitter.emitOtpUpdate(jsonString)
        //sreactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java).emit("","")
    }

    override fun sendTickUpdate(jsonString: String) {
        EventEmitter.emitTickUpdate(jsonString)
    }

}