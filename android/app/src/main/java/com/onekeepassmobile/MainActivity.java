package com.onekeepassmobile;

import android.content.Intent;
import android.os.Bundle;

import com.facebook.react.ReactActivity;
import com.facebook.react.ReactActivityDelegate;
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint;
import com.facebook.react.defaults.DefaultReactActivityDelegate;
import com.zoontek.rnbootsplash.RNBootSplash;

public class MainActivity extends ReactActivity {

    /**
     * Returns the name of the main component registered from JavaScript. This is used to schedule
     * rendering of the component.
     */
    @Override
    protected String getMainComponentName() {
        return "OneKeePassMobile";
    }

    /**
     * Returns the instance of the {@link ReactActivityDelegate}. Here we use a util class {@link
     * DefaultReactActivityDelegate} which allows you to easily enable Fabric and Concurrent React
     * (aka React 18) with two boolean flags.
     */
    @Override
    protected ReactActivityDelegate createReactActivityDelegate() {
        return new DefaultReactActivityDelegate(
                this,
                getMainComponentName(),
                // If you opted-in for the New Architecture, we enable the Fabric Renderer.
                DefaultNewArchitectureEntryPoint.getFabricEnabled(), // fabricEnabled
                // If you opted-in for the New Architecture, we enable Concurrent React (i.e. React 18).
                DefaultNewArchitectureEntryPoint.getConcurrentReactEnabled() // concurrentRootEnabled
        );
    }

    /**
     * This is called when the app is launched first time
     *
     * @param savedInstanceState If the activity is being re-initialized after
     *                           previously being shut down then this Bundle contains the data it most
     *                           recently supplied in {@link #onSaveInstanceState}.  <b><i>Note: Otherwise it is null.</i></b>
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // initialize the splash screen and need to call hide with duration in UI side
        // See the use of react-use-effect in onekeepass.mobile.core.main fn
        RNBootSplash.init(this);

        super.onCreate(savedInstanceState);

        // We may receive an intent for android.intent.action.VIEW if the user presses a db file with
        // the extension .kdbx and the app is not previously running and it is started now
        // See the required intent-filter that are added for this in AndroidManifest.xml
        EventEmitter.INSTANCE.onCreateIntent(getIntent());

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
    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        EventEmitter.INSTANCE.onNewIntent(intent);
    }
}