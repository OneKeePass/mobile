package com.onekeepassmobile

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.util.Log
import com.facebook.react.bridge.*

private const val TAG = "OkpDocumentPicker";

class DocumentPickerServiceModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    private var pickerPromise: Promise? = null

    // IMPORTANT: We need to have an unique request code
    // as all registered activityEventListeners will receive the result
    // See ExportServiceModule where other result codes are declared
    // TODO: Need to make a singleton
    private val activityEventListener =
            object : BaseActivityEventListener() {
                override fun onActivityResult(activity: Activity?, requestCode: Int, resultCode: Int, intent: Intent?) {
                    Log.d(TAG, "requestCode code is $requestCode resultCode $resultCode intent $intent for activity $activity")
                    when (requestCode) {
                        PICK_DIR_REQUEST_CODE -> {
                            pickerPromise?.let { promise ->
                                when (resultCode) {
                                    Activity.RESULT_CANCELED ->
                                        promise.reject(E_DOCUMENT_PICKER_CANCELED, "Document picker was cancelled..")
                                    Activity.RESULT_OK -> {
                                        val uri = intent?.data
                                        uri?.let { promise.resolve(uri.toString()) }
                                                ?: promise.reject(E_INVALID_DATA_RETURNED, "No Document data found..")
                                    }
                                }
                                pickerPromise = null
                            }
                        }
                        // PICK_KDBX_FILE_CODES includes PICK_KDBX_FILE_CREATE_REQUEST_CODE, PICK_KDBX_FILE_OPEN_REQUEST_CODE
                        in PICK_KDBX_FILE_CODES -> {
                            when (resultCode) {
                                Activity.RESULT_CANCELED ->
                                    pickerPromise?.reject(E_DOCUMENT_PICKER_CANCELED, "Document to save or read was cancelled")
                                Activity.RESULT_OK -> {
                                    // TODO: Need to include check ClipData and get the first uri
                                    // See https://github.com/rnmods/react-native-document-picker/blob/0311eb6cf8d7eb9bb7d7b73a1345a47c8601f245/android/src/main/java/com/reactnativedocumentpicker/DocumentPickerModule.java#L190
                                    val uri = intent?.data
                                    uri?.let {
                                        FileUtils.getMetaInfo(contentResolver, it)
                                        //Need to ensure the required permissions taken for the future use of this document
                                        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION and
                                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION  //and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                                        contentResolver.takePersistableUriPermission(uri, takeFlags)
                                    }
                                    //
                                    if (requestCode == PICK_KDBX_FILE_OPEN_REQUEST_CODE) {
                                        pickerPromise?.resolve(DbServiceAPI.formJsonWithFileName(uri.toString()))
                                    } else {
                                        // As we are sending just the uri value, this is converted as
                                        // json string {:ok resolved} in transform-api-response function
                                        pickerPromise?.resolve(uri.toString())
                                    }
                                }
                            }
                            pickerPromise = null
                        }
                    }
                }
            }

    val contentResolver: ContentResolver = reactContext.contentResolver

    init {
        // As we have also added activityEventListener in ExportServiceModule,
        // both listeners will be called when any activity returns from our app
        reactContext.addActivityEventListener(activityEventListener)
    }

    override fun getName() = "OkpDocumentPickerService"

    @ReactMethod
    fun pickDirectory(promise: Promise) {
        val currentActivity = currentActivity
        if (currentActivity == null) {
            promise.reject(E_ACTIVITY_DOES_NOT_EXIST, "Current activity does not exist")
            return
        }
        pickerPromise = promise
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            currentActivity.startActivityForResult(intent, PICK_DIR_REQUEST_CODE, null)
        } catch (e: Exception) {
            promise.reject(E_FAILED_TO_SHOW_PICKER, "Failed to create directory picker", e)
        }
    }

    // Called to show a file picker view for the user to select a file with the passed name to save
    // something similar to Save as.
    // This initiates an ACTION_CREATE_DOCUMENT
    //
    // It appears 'ACTION_CREATE_DOCUMENT' based intent works only with GDrive,DropBox and Local only
    // See https://stackoverflow.com/questions/64730497/missing-document-providers-using-intent-actioncreatedocument
    // For other cloud storages, we may need to use ACTION_SEND to save the newly created database file first and followed
    // by ACTION_OPEN_DOCUMENT to open the db
    @ReactMethod
    fun pickKdbxFileToCreate(fileName: String, promise: Promise) {
        val currentActivity = currentActivity
        if (currentActivity == null) {
            promise.reject(E_ACTIVITY_DOES_NOT_EXIST, "Current activity does not exist")
            return
        }
        pickerPromise = promise
        try {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                // To return only "openable" files that can be represented as a file stream with openFileDescriptor
                addCategory(Intent.CATEGORY_OPENABLE)
                //type = "*/*"
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_TITLE, fileName)

                // Optionally, specify a URI for the directory that should be opened in
                // the system file picker before your app creates the document.
                //putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
            }
            currentActivity.startActivityForResult(intent, PICK_KDBX_FILE_CREATE_REQUEST_CODE, null)
        } catch (e: Exception) {
            promise.reject(E_FAILED_TO_SHOW_PICKER, "Failed to create document picker", e)
        }
    }

    /**
     * Provides an UI view for the user to select a kdbx file to open and read
     */
    @ReactMethod
    fun pickKdbxFileToOpen(promise: Promise) {
        val currentActivity = currentActivity
        if (currentActivity == null) {
            promise.reject(E_ACTIVITY_DOES_NOT_EXIST, "Current activity does not exist")
            return
        }
        pickerPromise = promise
        try {
            // When we use Intent.ACTION_GET_CONTENT, only the google drive and photos folders show
            // From https://developer.android.com/guide/components/intents-common#GetFile
            // The file reference returned to your app is transient to your activity's current lifecycle,
            // so if you want to access it later you must import a copy that you can read later

            // By using ACTION_OPEN_DOCUMENT, we can see other locations also to open a file

            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                // To return only "openable" files that can be represented as a file stream with openFileDescriptor
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
//                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
//                addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
//                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

                // Optionally, specify a URI for the directory that should be opened in
                // the system file picker before your app creates the document.
                //putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
            }
            currentActivity.startActivityForResult(intent, PICK_KDBX_FILE_OPEN_REQUEST_CODE, null)
        } catch (e: Exception) {
            promise.reject(E_FAILED_TO_SHOW_PICKER, "Failed in document picker to open a document", e)
        }
    }

    companion object {
        // IMPORTANT: We need to have an unique request code
        // as all registered activityEventListeners will receive the result
        private const val PICK_DIR_REQUEST_CODE = 1
        private const val PICK_KDBX_FILE_CREATE_REQUEST_CODE = 2
        private const val PICK_KDBX_FILE_OPEN_REQUEST_CODE = 3

        private const val E_ACTIVITY_DOES_NOT_EXIST = "ACTIVITY_DOES_NOT_EXIST"
        private const val E_FAILED_TO_SHOW_PICKER = "FAILED_TO_SHOW_PICKER"
        private const val E_DOCUMENT_PICKER_CANCELED = "DOCUMENT_PICKER_CANCELED"
        private const val E_UNABLE_TO_OPEN_FILE_TYPE = "UNABLE_TO_OPEN_FILE_TYPE"
        private const val E_UNKNOWN_ACTIVITY_RESULT = "UNKNOWN_ACTIVITY_RESULT"
        private const val E_INVALID_DATA_RETURNED = "INVALID_DATA_RETURNED"
        private const val E_UNEXPECTED_EXCEPTION = "UNEXPECTED_EXCEPTION"

        private val PICK_KDBX_FILE_CODES = arrayOf(PICK_KDBX_FILE_CREATE_REQUEST_CODE, PICK_KDBX_FILE_OPEN_REQUEST_CODE)
    }
}
