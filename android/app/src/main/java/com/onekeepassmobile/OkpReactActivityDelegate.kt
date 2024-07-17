package com.onekeepassmobile

import android.content.ContextWrapper
import android.content.MutableContextWrapper
import android.os.Bundle
import android.util.Log
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.ReactRootView

//import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled

class OkpReactActivityDelegate(val activity: ReactActivity,
                               mainComponentName: String,
                               val fabricEnabled: Boolean) : ReactActivityDelegate(activity, mainComponentName) {

    //var okpRootView:ReactRootView? = null

    override fun isFabricEnabled(): Boolean = fabricEnabled

    override fun createRootView(): ReactRootView {
        Log.d(TAG, "createRootView 1 is called")
        val rrview = ReactRootView(context).apply { setIsFabric(fabricEnabled) }
        okpRootView = rrview
        Log.d(TAG, "createRootView 1 Returning a new  ReactRootView")
        return rrview
        /*
        if (okpRootView != null) {
            Log.d(TAG, "createRootView 1 Returning previously created ReactRootView")
            return okpRootView!!
        } else {
            val rrview = ReactRootView(context).apply { setIsFabric(fabricEnabled) }
            okpRootView = rrview
            Log.d(TAG, "createRootView 1 Returning a new  ReactRootView")
            return rrview
        }

         */
    }

    override fun createRootView(bundle: Bundle?): ReactRootView {
        Log.d(TAG, "createRootView 2 is called")

//        val ctx = MutableContextWrapper(activity)
//        val rrview = ReactRootView(ctx).apply { setIsFabric(fabricEnabled) }
//        okpRootView = rrview
//        Log.d(TAG, "createRootView 2 Returning a new  ReactRootView")
//        return rrview


        if (okpRootView != null) {
            Log.d(TAG, "createRootView 2 Returning previously created ReactRootView")
            (okpRootView!!.context as MutableContextWrapper).baseContext = activity
            return okpRootView!!
        } else {
            val ctx = MutableContextWrapper(activity)
            val rrview = ReactRootView(ctx).apply { setIsFabric(fabricEnabled) }
            okpRootView = rrview
            Log.d(TAG, "createRootView 2 Returning a new  ReactRootView")
            return rrview
        }
    }

    override fun onCreate(savedInstanceState:Bundle?) {
        Log.d(TAG, "onCreate is called")
        if (okpRootView != null) {
            Log.d(TAG, "Super onCreate is NOT called")
            return
        }

        Log.d(TAG, "Super onCreate is called")
        super.onCreate(savedInstanceState)
    }



    companion object {
        private val TAG = "OneKeePass"
        var okpRootView:ReactRootView? = null
    }
}