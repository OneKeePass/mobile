package com.onekeepassmobile

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.facebook.react.bridge.*
import java.io.File

// See in app/src/main/AndroidManifest.xml the entries under the element
// provider and also see app/src/main/res/xml/file_paths.xml. We need to include
// app's dir for the export to work

private const val TAG = "ExportServiceModule"

class ExportServiceModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    override fun getName() = "OkpExport"

    // Domain authority for our app FileProvider
    // This should be the same that is used in @res/xml/file_paths.xml
    private val fileProviderAuthority = "com.onekeepassmobile.fileprovider"

    private var modulePromise: Promise? = null

    // IMPORTANT: We need to have an unique request code
    // as all registered activityEventListeners will receive the result
    // See DocumentPickerServiceModule where other result codes are declared
    // TODO: Need to make a singleton by combining theses codes in one place
    private val EXPORT_DATA_REQUEST_CODE = 150

    private val activityEventListener =
            object : BaseActivityEventListener() {
                override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, intent: Intent?) {
                    Log.d(TAG, "requestCode code is $requestCode resultCode $resultCode intent $intent for activity $activity")
                    if (requestCode == EXPORT_DATA_REQUEST_CODE) {
                        modulePromise?.let { promise ->
                            // Need to check resultCode on device testing and resolve accordingly
                            // By this time the export data would have been exported
                            // Not sure what happens if the delete happens before the export is completed
                            promise.resolve(DbServiceAPI.cleanExportDataDir())
                            modulePromise = null
                        }
                    }
                }
            }

    init {
        // As we have also added activityEventListener in DocumentPickerServiceModule,
        // both will be fired when any activity returns from our app
        reactContext.addActivityEventListener(activityEventListener)
    }

    @ReactMethod
    fun exportKdbx(fullFileNameUri: String, promise: Promise) {
        Log.d(TAG, "exportKdbx is called with fullFileNameUri $fullFileNameUri")

        val currentActivity = reactApplicationContext.currentActivity
        if (currentActivity == null) {
            promise.reject("ExportServiceModule", "Current activity does not exist")
            return
        }
        val exportDataFile = File(fullFileNameUri)
        val contentUri: Uri = FileProvider.getUriForFile(reactApplicationContext, fileProviderAuthority, exportDataFile)

        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, contentUri)
            // These two types bring different share screen
            //type = "*/*"
            type = "application/octet-stream"

            // If we use the following flag, the activity is started and share sheet is shown
            // and also our app receives the activity result though the user has not yet completed
            // any action. Without this, we receive the result only user completes the action in the share
            // sheet

            //addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        modulePromise = promise
        //currentActivity.startActivity(sendIntent)
        currentActivity.startActivityForResult(sendIntent, EXPORT_DATA_REQUEST_CODE)
        // Calling createChooser instead of above will show different share sheet
        //currentActivity.startActivity(Intent.createChooser(intent,"My File"))
        Log.d(TAG, "Started new activity to export...")
    }
}