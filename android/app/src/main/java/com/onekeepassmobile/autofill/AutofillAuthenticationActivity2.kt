package com.onekeepassmobile.autofill

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import com.facebook.react.ReactApplication
import com.facebook.react.ReactInstanceManager
import com.facebook.react.ReactRootView
import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler
import com.facebook.soloader.SoLoader

class AutofillAuthenticationActivity2 : Activity(), DefaultHardwareBackBtnHandler {

    private lateinit var reactRootView: ReactRootView
    private lateinit var reactInstanceManager: ReactInstanceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SoLoader.init(this, false)
        reactRootView = ReactRootView(this)

        reactInstanceManager = (application as ReactApplication).reactNativeHost.reactInstanceManager

        Log.d(TAG, "Got reactInstanceManager from application $reactInstanceManager")
        val initialProperties = Bundle().apply {
            putBoolean("androidAutofill", true)
            putString("anotherKey", "some value")
        }
        reactRootView?.startReactApplication(reactInstanceManager, "OneKeePassMobile", initialProperties)

        autofillActivity = this
        Log.d(TAG, "Started reactRootView ....and setting the root view")
        setContentView(reactRootView)
    }

    override fun invokeDefaultOnBackPressed() {
        Log.d(TAG, "AF invokeDefaultOnBackPressed is called ")
        super.onBackPressed()
    }

    override fun onPause() {
        Log.d(TAG, "AF onPause is called ")
        super.onPause()
        reactInstanceManager.onHostPause(this)

        autofillActivity = null
        Log.d(TAG, "AF onPause ends")
        // Need to use some emit event to cljs here ?
    }

    override fun onResume() {
        Log.d(TAG, "AF onResume start")
        super.onResume()
        reactInstanceManager.onHostResume(this, this)

        autofillActivity = this
        Log.d(TAG, "AF onResume is called ")
        // Need to use some emit event to cljs here ?
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AF onDestroy is called ")
        reactInstanceManager.onHostDestroy(this)
        reactRootView.unmountReactApplication()
    }

    override fun onBackPressed() {
        Log.d(TAG, "AF onBackPressed is called and reactInstanceManager.onBackPressed will be called ")
        reactInstanceManager.onBackPressed()
        super.onBackPressed()
        // Need to use some emit event to cljs here ?
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "AF onActivityResult is called with requestCode: $requestCode, resultCode: $resultCode, intent:$data")
        super.onActivityResult(requestCode, resultCode, data)
        reactInstanceManager.onActivityResult(this,requestCode,resultCode,data)
    }

    companion object {
        //private val TAG = "AutofillAuthenticationActivity"
        private val TAG = "OkpAF AutofillAuthenticationActivity2"

        private var autofillActivity:AutofillAuthenticationActivity2? = null

        fun getActivityToComplteFill():AutofillAuthenticationActivity2? {
            return autofillActivity
        }
    }
}