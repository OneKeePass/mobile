package com.onekeepassmobile.autofill

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import com.facebook.react.ReactApplication
import com.facebook.react.ReactInstanceManager
import com.facebook.react.ReactRootView
import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler
import com.facebook.soloader.SoLoader


// This Activity for autofill extends the standard Activity and not ReactActivity.
// The relevant activity functions are changed to use the view content from react native

// Somewhat based on https://reactnative.dev/docs/0.73/integration-with-existing-apps and
// some other suggestions seen on various places - see docs

// As the 'MainApplication' creates the 'ReactInstanceManager' when the main app is launched or
// when the Autofill activity is launched, that instance is shared by both activities

// Alternative method possible:
// As both activities are using the same "OneKeePassMobile" component(in /mobile/app.json) 
// and used by the AppRegistry.registerComponent call in index.js, we can extend
// this activity from ReactActivity. Then we need not get an instance of ReactInstanceManager
// and delegate activities call to this manager


class AutofillAuthenticationActivity : Activity(), DefaultHardwareBackBtnHandler {

    private lateinit var reactRootView: ReactRootView
    private lateinit var reactInstanceManager: ReactInstanceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SoLoader.init(this, false)
        reactRootView = ReactRootView(this)

        reactInstanceManager = (application as ReactApplication).reactNativeHost.reactInstanceManager

        Log.d(TAG, "Got reactInstanceManager from application $reactInstanceManager")

        // As we use the same cljs bundle, this initial args distinguish from Android
        // main activity vs Android autofill activity
        val initialProperties = Bundle().apply {
            putBoolean("androidAutofill", true)
            //putString("anotherKey", "some value")
        }
        reactRootView.startReactApplication(reactInstanceManager, "OneKeePassMobile", initialProperties)

        autofillActivity = this
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

        // As we handle back press event in JS side, do not call 'super.onBackPressed()'
        // invokeDefaultOnBackPressed will be called based on the JS 'hardwareBackPress'
        
        //super.onBackPressed()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "AF onActivityResult is called with requestCode: $requestCode, resultCode: $resultCode, intent:$data")
        super.onActivityResult(requestCode, resultCode, data)
        reactInstanceManager.onActivityResult(this,requestCode,resultCode,data)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU && reactInstanceManager != null) {
            reactInstanceManager.showDevOptionsDialog()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    companion object {
        //private val TAG = "AutofillAuthenticationActivity"
        private val TAG = "OkpAF AutofillAuthenticationActivity"

        // Keeps a reference of this activity and it is used to complete the autofill
        private var autofillActivity:AutofillAuthenticationActivity? = null

        fun getActivityToComplteFill():AutofillAuthenticationActivity? {
            return autofillActivity
        }
    }
}