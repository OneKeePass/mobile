package com.onekeepassmobile

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.facebook.react.bridge.*
import java.io.FileNotFoundException

private const val TAG = "OkpDocumentPicker";

class DocumentPickerServiceModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    private var pickerPromise: Promise? = null
    // See 'pickKeyFileToSave'
    private var fullKeyFileToSave: String? = null

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
                                        //IMPORTANT: We need to use kotlin bitwise or operator to get both READ and WRITE permissions
                                        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                        contentResolver.takePersistableUriPermission(uri, takeFlags)
                                    }
                                    pickerPromise?.resolve(DbServiceAPI.formJsonWithFileName(uri.toString()))
                                }
                            }
                            pickerPromise = null
                        }
                        PICK_KEY_FILE_OPEN_REQUEST_CODE -> {
                            when (resultCode) {
                                Activity.RESULT_CANCELED ->
                                    pickerPromise?.reject(E_DOCUMENT_PICKER_CANCELED, "Document to read was cancelled")
                                Activity.RESULT_OK -> {
                                    // See https://github.com/rnmods/react-native-document-picker/blob/0311eb6cf8d7eb9bb7d7b73a1345a47c8601f245/android/src/main/java/com/reactnativedocumentpicker/DocumentPickerModule.java#L190
                                    val uri = intent?.data
                                    uri?.let {
                                        //Need to ensure the required permissions taken for the future use of this document
                                        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        contentResolver.takePersistableUriPermission(uri, takeFlags)
                                    }
                                    // Returning the selected file's full uri as a proper json object
                                    pickerPromise?.resolve(DbServiceAPI.jsonService().okJsonString(uri.toString()))
                                }
                            }
                            pickerPromise = null
                        }
                        PICK_KEY_FILE_SAVE_AS_REQUEST_CODE -> {
                            when (resultCode) {
                                Activity.RESULT_CANCELED ->
                                    pickerPromise?.reject(E_DOCUMENT_PICKER_CANCELED, "Document to save was cancelled")
                                Activity.RESULT_OK -> {
                                    // See https://github.com/rnmods/react-native-document-picker/blob/0311eb6cf8d7eb9bb7d7b73a1345a47c8601f245/android/src/main/java/com/reactnativedocumentpicker/DocumentPickerModule.java#L190
                                    val uri = intent?.data
                                    uri?.let {
                                        //Need to ensure the required permissions taken for the future use of this document
                                        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION and
                                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                        contentResolver.takePersistableUriPermission(uri, takeFlags)
                                    }
                                    // Returning saved result as json object
                                    //pickerPromise?.resolve(saveKeyFile(uri.toString(), fullKeyFileToSave!!, pickerPromise!!))

                                    pickerPromise?.let {
                                        /*
                                            fullKeyFileToSave?.also { kfName ->
                                                it.resolve(saveKeyFile(uri.toString(),kfName, it))
                                            } ?: {
                                            // This was not called when fullKeyFileToSave is null. Leaving it here to check why
                                                Log.e("INTERNAL_ERROR","fullKeyFileToSave is null and it should have a valid value")
                                                it.reject("INTERNAL_ERROR","fullKeyFileToSave is null and it should have a valid value")
                                            }
                                         */

                                        if (fullKeyFileToSave != null) {
                                            it.resolve(saveKeyFile(uri.toString(),fullKeyFileToSave!!, it))
                                        } else {
                                            Log.e("INTERNAL_ERROR","fullKeyFileToSave is null and it should have a valid value")
                                            it.reject("INTERNAL_ERROR","Key file path name should have a valid value")
                                        }
                                    }
                                    fullKeyFileToSave = null
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

    /**
     * Provides an UI view for the user to select any file to copy as key file
     */
    @ReactMethod
    fun pickKeyFileToCopy(promise: Promise) {
        val currentActivity = currentActivity
        if (currentActivity == null) {
            promise.reject(E_ACTIVITY_DOES_NOT_EXIST, "Current activity does not exist")
            return
        }
        pickerPromise = promise
        try {

            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                // To return only "openable" files that can be represented as a file stream with openFileDescriptor
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                // Optionally, specify a URI for the directory that should be opened in
                // the system file picker before your app creates the document.
                //putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
            }
            currentActivity.startActivityForResult(intent, PICK_KEY_FILE_OPEN_REQUEST_CODE, null)
        } catch (e: Exception) {
            promise.reject(E_FAILED_TO_SHOW_PICKER, "Failed in document picker to open a document", e)
        }
    }

    /**
     * Provides an UI view for the user to do save as operation. This is similar to 'pickKdbxFileToCreate'
     * but we save the file in the activity handler itself!
     */
    @ReactMethod
    fun pickKeyFileToSave(fullKeyFileName:String,fileName: String, promise: Promise) {
        // Needs to save the original 'fullKeyFileName' to use in 'PICK_KEY_FILE_SAVE_AS_REQUEST_CODE'
        // handler
        fullKeyFileToSave = fullKeyFileName
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
            }
            currentActivity.startActivityForResult(intent, PICK_KEY_FILE_SAVE_AS_REQUEST_CODE, null)
        } catch (e: Exception) {
            promise.reject(E_FAILED_TO_SHOW_PICKER, "Failed to create document picker", e)
        }
    }

    /**
     *  Called once user picks a location with name change if any
     *  @param pickedFullFileNameUri is the final name from Activity handler
     *  @param localKeyFileFullName
     *  is the internal key file full path passed from cljs side and it is set to the instance variable
     *  fullKeyFileToSave in pickKeyFileToSave
     *
     */
    fun saveKeyFile(pickedFullFileNameUri:String,localKeyFileFullName:String, promise:Promise) {
        Log.i(TAG, "Received fullFileName is $pickedFullFileNameUri")
        val uri = Uri.parse(pickedFullFileNameUri);
        Log.i(TAG, "Parsed uri is $uri")
        try {
            // Here it is assumed the fileNameUri starts with content:// for which there will be
            // a resolver. If we use internal file path, we need to do something similar to readKdbx

            // See rational to use mode:rwt here https://issuetracker.google.com/issues/180526528
            // Our requirement is at least "rw"
            val fd: ParcelFileDescriptor? = contentResolver.openFileDescriptor(uri, "rwt");
            // Log.d(TAG, "After FileDescriptor.${fd}")
            if (fd != null) {

                // Rust side the file should not be closed. So fd.detachFd() is not used.
                // if we use fd.detachFd(), then writing did not work for some cases (Gdrive ?)
                // If the rust side fails to transfer the ownership of this fd, the app may crash
                // See comments in creat_kdbx, save_key_file methods accordingly
                val response = DbServiceAPI.androidSupportService().saveKeyFile(fd.fd.toULong(),localKeyFileFullName)
                promise.resolve(response)

                // IMPORTANT:
                // The caller is responsible for closing the file using its file descriptor
                // as the file is not yet closed
                fd.close()

            } else {
                // Do we need to ask the user select the kdbx again to read ?
                promise.reject("CREATE_FIE_DESCRIPTOR_ERROR", "Invalid file descriptor in saveKeyFile")
            }
        } catch (e: SecurityException) {
            // This will happen, if we try to create the kdbx file without proper read and write permissions
            // We need to obtain while selecting the file. See DocumentPickerServiceModule
            Log.e(TAG, "SecurityException due to in sufficient permission")
            promise.reject("PERMISSION_REQUIRED_TO_WRITE", e)
        } catch (e: FileNotFoundException) {
            // Need to add logic in UI layer to handle this
            Log.e(TAG, "Error in createKdbx ${e}")
            // e.printStackTrace()
            promise.reject("FILE_NOT_FOUND", e)
        } catch (e: Exception) {
            // e.printStackTrace()
            Log.e(TAG, "Error in createKdbx ${e}")
            promise.reject("KEY_FILE_SAVE_CALL_FAILED", e)
        }
    }

    companion object {
        // IMPORTANT: We need to have an unique request code
        // as all registered activityEventListeners will receive the result
        private const val PICK_DIR_REQUEST_CODE = 1
        private const val PICK_KDBX_FILE_CREATE_REQUEST_CODE = 2
        private const val PICK_KDBX_FILE_OPEN_REQUEST_CODE = 3

        private const val PICK_KEY_FILE_OPEN_REQUEST_CODE =  40
        private const val PICK_KEY_FILE_SAVE_AS_REQUEST_CODE =  41

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
