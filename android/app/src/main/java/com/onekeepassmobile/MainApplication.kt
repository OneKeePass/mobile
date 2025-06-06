package com.onekeepassmobile

import android.app.Application
import android.content.Context
import android.util.Log
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.load
import com.facebook.react.defaults.DefaultReactNativeHost
import com.facebook.react.soloader.OpenSourceMergedSoMapping
import com.facebook.soloader.SoLoader

class MainApplication : Application(), ReactApplication {
    override val reactNativeHost: ReactNativeHost = object : DefaultReactNativeHost(this) {
        override fun getUseDeveloperSupport(): Boolean {
            return BuildConfig.DEBUG
        }

        override fun getPackages(): List<ReactPackage>  {
            Log.d(TAG, "Returning OKP Native module package implementation in getPackages call")
            return PackageList(this).packages.apply {
                // Packages that cannot be autolinked yet can be added manually here
                // TODO (Custom - Jey)
                // OneKeePassAppPackage should be added as here to ensure loading of the app's NativeModules
                Log.d(TAG, "Added OneKeePassAppPackage")
                add(OneKeePassAppPackage())
            }
        }

        override fun getJSMainModuleName(): String {
            Log.d(TAG, "JSMainModuleName returns 'index' name")
            return "index"
        }

        override val isNewArchEnabled: Boolean
            get() = BuildConfig.IS_NEW_ARCHITECTURE_ENABLED
        override val isHermesEnabled: Boolean
            get() = BuildConfig.IS_HERMES_ENABLED
    }

    override fun onCreate() {
        Log.d(TAG, "MainApplication.onCreate is called")
        super.onCreate()
        SoLoader.init(this,  OpenSourceMergedSoMapping)
        if (BuildConfig.IS_NEW_ARCHITECTURE_ENABLED) {
            // If you opted-in for the New Architecture, we load the native entry point for this app.
            load()
        }
        
        setGlobal(this)
    }

    companion object {
        private val TAG = "MainApplication"

        private lateinit var mainApplication: MainApplication

        fun setGlobal(app:MainApplication) {
            mainApplication = app
            // Custom clipboard manager handling
            OkpClipboardManager.setClipboardManager(app.applicationContext)
        }

        fun getInstance(): MainApplication {
            return mainApplication
        }

        fun getInstanceContext(): Context {
            return mainApplication.applicationContext
        }
    }
}