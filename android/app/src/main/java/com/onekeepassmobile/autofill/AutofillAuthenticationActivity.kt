package com.onekeepassmobile.autofill

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate

// RN 0.81.5 upgrade time
// As both 'MainActivity' and 'AutofillAuthenticationActivity' activities are using
// the same "OneKeePassMobile" as main component name
// and used by the AppRegistry.registerComponent call in index.js, we can extend
// this activity from ReactActivity. We use 'initialProperties' during the launch time of Autofill page
// so that we can distinguish between Main app vs Autofill app

// As this is a subclass of  ReactActivity', all 'DefaultHardwareBackBtnHandler' actions are available for this
// activity

//@TargetApi(26)
@RequiresApi(Build.VERSION_CODES.O)
class AutofillAuthenticationActivity() : ReactActivity() {
    override fun getMainComponentName(): String? {
        // This component name declared in /mobile/app.json and used
        // by AppRegistry.registerComponent call in index.js
        return "OneKeePassMobile"
    }

    override fun createReactActivityDelegate(): ReactActivityDelegate {
        Log.d(TAG, "Creating and returning DefaultReactActivityDelegate")

        val activityDelegate:DefaultReactActivityDelegate = object:DefaultReactActivityDelegate(this,mainComponentName!!,fabricEnabled) {
            override fun getLaunchOptions(): Bundle? {
                Log.d(TAG, "AutofillAuthenticationActivity getLaunchOptions is called")
                val initialProperties = Bundle().apply {
                    putBoolean("androidAutofill", true)
                    //putString("anotherKey", "some value")
                }
                return initialProperties
            }
        }
        return activityDelegate
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("AutofillAuthenticationActivity", "onCreate of AutofillAuthenticationActivity is called ...with intent ${intent}")
        autofillActivity = this
        super.onCreate(savedInstanceState)
    }

    companion object {
        //private val TAG = "AutofillAuthenticationActivity"
        private val TAG = "OkpAF AutofillAuthenticationActivity"

        // Keeps a reference of this activity and it is used in  to complete the autofill
        private var autofillActivity: AutofillAuthenticationActivity? = null

        // Called from OkpFillResponseBuilder.completeLoginAutofill fn
        fun getActivityToComplteFill(): AutofillAuthenticationActivity? {
            return autofillActivity
        }
    }
}


//===========================================================//

// Legacy Architecture is used
// The implementation is based on
// https://reactnative-archive-august-2025.netlify.app/docs/0.74/integration-with-existing-apps#the-magic-reactrootview
// And was used in RN 0.74 upgrade and in 0.78 upgrade time
// This also worked with RN 0.81.5 upgarde

// The 'DefaultHardwareBackBtnHandler' use will not work new Android and the following deprecated info is found here
// https://reactnative.dev/docs/0.81/integration-with-android-fragment#3-make-your-host-activity-implement-defaulthardwarebackbtnhandler
// Deprecated:
// Activity.onBackPressed() has been deprecated since API level 33.
// Android 16 devices with apps targeting API level 36 this will
// no longer be called and OnBackPressedDispatcher should be used instead.

// Using OnBackPressedDispatcher involves more changes. However, by subclassing AutofillAuthenticationActivity
// from 'ReactActivity', we can avoid this additional works and accordingly changed as done above

/*
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
import com.facebook.react.soloader.OpenSourceMergedSoMapping
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
        SoLoader.init(this, OpenSourceMergedSoMapping)
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
 */

