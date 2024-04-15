package com.onekeepassmobile

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.modules.core.DeviceEventManagerModule
import onekeepass.mobile.ffi.EventDispatch

// This is the callback implementation of the Trait 'EventDispatch' in in db_service.udl
// We can not use name 'EventDispatchImpl' as this is used in the generated code db_service.kt
class BackendEventDispatcher(val reactContext: ReactApplicationContext) : EventDispatch {

    // This is called from rust side
    override fun sendOtpUpdate(jsonString: String) {
        EventEmitter.emitOtpUpdate(jsonString)
    }

    // This is called from rust side
    override fun sendTickUpdate(jsonString: String) {
        EventEmitter.emitTickUpdate(jsonString)
    }
}