package com.onekeepassmobile

import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.ReactApplication
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate
import com.onekeepassmobile.EventEmitter
import com.zoontek.rnbootsplash.RNBootSplash

class MainActivity : ReactActivity() {
    /**
     * Returns the name of the main component registered from JavaScript. This is used to schedule
     * rendering of the component.
     */
    override fun getMainComponentName(): String? {
        // This component name declared in /mobile/app.json and used 
        // by AppRegistry.registerComponent call in index.js
        return "OneKeePassMobile"
    }

    /**
     * Returns the instance of the [ReactActivityDelegate]. Here we use a util class [ ] which allows you to easily enable Fabric and Concurrent React
     * (aka React 18) with two boolean flags.
     */
    override fun createReactActivityDelegate(): ReactActivityDelegate {
        //Log.d(TAG, "Creating and returning DefaultReactActivityDelegate")

        return DefaultReactActivityDelegate(
                this,
                mainComponentName!!,  // If you opted-in for the New Architecture, we enable the Fabric Renderer.
                fabricEnabled
        )
    }

    /**
     * This is called when the app is launched first time
     *
     * @param savedInstanceState If the activity is being re-initialized after
     * previously being shut down then this Bundle contains the data it most
     * recently supplied in [.onSaveInstanceState].  ***Note: Otherwise it is null.***
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("MainActivity", "onCreate is called...with intent ${intent}")
        // initialize the splash screen and need to call hide with duration in UI side
        // See the use of react-use-effect in onekeepass.mobile.core.main fn
        RNBootSplash.init(this)

        super.onCreate(savedInstanceState)
        
        // We may receive an intent for android.intent.action.VIEW if the user presses a db file with
        // the extension .kdbx and the app is not previously running and it is started now
        // See the required intent-filter that are added for this in AndroidManifest.xml
        EventEmitter.onCreateIntent(intent)

//    https://stackoverflow.com/questions/49153747/how-can-i-get-the-current-reactcontext-in-mainactivitys-oncreate-function
//    Though the ReactApplicationContext is available so we can do emitKdbxUriToOpenEvent, the event
//    gets sent to UI layer before we could register any listener in Ui layer side
//    The pull method in EventEmitter.kdbxUriToOpenOnCreate from JS to Native side works and used

        /*
    ReactInstanceManager mReactInstanceManager = getReactNativeHost().getReactInstanceManager();
    mReactInstanceManager.addReactInstanceEventListener(new ReactInstanceEventListener() {
      @Override
      public void onReactContextInitialized(ReactContext reactContext) {
          Log.d("MainActivity", String.format("onReactContextInitialized with reactContext %s", reactContext));
          //EventEmitter.INSTANCE.emitKdbxUriToOpenEvent((ReactApplicationContext) reactContext);
      }
    });
  */
    }

    /**
     * This is called whenever user selects kdbx file with the extension .kdbx in Files app
     * See the required intent-filter that are added for this in AndroidManifest.xml
     *
     * @param intent The new intent that was started for the activity.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("MainActivity", "onNewIntent is called...with intent ${intent}")
        EventEmitter.onNewIntent(intent)
        //Log.d("MainActivity", "EventEmitter.onNewIntent is called...")
    }

    override fun onPause() {
        super.onPause();
        Log.d("MainActivity", "On pause is called...")
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "On resume is called...")
    }

    companion object {
        private val TAG = "MainActivity"
    }
}