package com.onekeepassmobile.autofill

import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.ReactApplication
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint
import com.facebook.react.defaults.DefaultReactActivityDelegate
import com.onekeepassmobile.MainActivity
import com.onekeepassmobile.OkpReactActivityDelegate

class AutofillAuthenticationActivity : ReactActivity() {

    override fun getMainComponentName(): String? {
        return "OneKeePassMobile"
    }

    override fun createReactActivityDelegate(): ReactActivityDelegate {
        Log.d(TAG, "Creating and returning DefaultReactActivityDelegate")
//        return DefaultReactActivityDelegate(
//                this,
//                mainComponentName!!,  // If you opted-in for the New Architecture, we enable the Fabric Renderer.
//                DefaultNewArchitectureEntryPoint.fabricEnabled
//        )

        return OkpReactActivityDelegate(this, mainComponentName!!, DefaultNewArchitectureEntryPoint.fabricEnabled)

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate is called...with intent ${intent}")

//        if (application is ReactApplication) {
//            val r = (application as ReactApplication).reactNativeHost.reactInstanceManager
//            Log.d(TAG,"The reactInstanceManager is $reactInstanceManager")
//        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent is called...with intent ${intent}")
    }

    override fun onPause() {
        super.onPause();
        Log.d(TAG, "On pause is called...")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "On resume is called...")
    }



    companion object {
        //private val TAG = "AutofillAuthenticationActivity"
        private val TAG = "OkpAF"
    }

}