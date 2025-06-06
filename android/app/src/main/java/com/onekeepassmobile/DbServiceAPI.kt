package com.onekeepassmobile

import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.facebook.react.bridge.ReactApplicationContext
import com.onekeepassmobile.DbServiceAPI.initialized
import onekeepass.mobile.ffi.*
import onekeepass.mobile.ffi.invokeCommand as dbServiceFFIInvokeCommand;
import onekeepass.mobile.ffi.ApiResponse
import java.io.FileNotFoundException

/**
 *  All ffi api calls go through this
 */

// tag:onekeepass_core | tag:DbServiceFFI | tag:db_service_ffi | tag:MainActivity | tag:DbServiceModule | tag:DbServiceAPI
private const val TAG = "DbServiceAPI";

object DbServiceAPI {

    // Provides apis that are called from Kotlin to rust implementations
    private var jsonService = onekeepass.mobile.ffi.JsonService();
    // private var androidSupportService = onekeepass.mobile.ffi.AndroidSupportService();

    // AndroidSupportService is declared in UDL file whereas AndroidSupportServiceExtra uses
    // uniffi annotations (macro attributes)
    private var androidSupportServiceExtra = onekeepass.mobile.ffi.AndroidSupportServiceExtra()

    // This flag 'initialized' is used so that we call rust lib initialization only one time
    // And this is mostly relevant during dev time when we do refreshing
    // the metro dev server to refresh the app
    private var initialized = false;

    init {
        Log.d(TAG, "Before calling dbServiceEnableLogging")
        dbServiceEnableLogging();
        Log.d(TAG, "After calling dbServiceEnableLogging")
    }

    fun initialize(reactContext: ReactApplicationContext) {
        Log.d(TAG, "Going to dbServiceInitialize and initialized is ${initialized}")

        // See the comments above about the requirement of using the initialized flag
        if (!initialized) {

            // For now separate initializations are done for these callback implementations and may
            // be moved to the dbServiceInitialize itself

            val apiCallBackService = ApiCallbackServiceImpl()
            // Need to call the rust side initialization fn so as to store this implementation
            // in rust store and api then can be called by rust code
            // ApiCallbackServiceImpl implements both interfaces for now
            androidCallbackServiceInitialize(apiCallBackService)
            //commonDeviceServiceExInitialize(apiCallBackService)

            dbServiceInitialize(
                CommonDeviceServiceImpl(reactContext),
                SecureKeyOperationImpl(reactContext),
                BackendEventDispatcher(reactContext),
                apiCallBackService,
                SecureEnclaveServiceSupport(reactContext),
            )

            initialized = true;

        } else {
            Log.d(TAG, "API initialize is already done")
        }
    }

    fun invokeCommand(commandName: String, args: String): String {
        return dbServiceFFIInvokeCommand(commandName, args)
    }

    fun androidInvokeCommand(commandName: String, args: String): String {
        return androidSupportServiceExtra.invoke(commandName, args)
    }

    fun androidSupportServiceExtra(): AndroidSupportServiceExtra {
        return androidSupportServiceExtra
    }

    fun cleanExportDataDir(): String {
        // Delegates to the invokeCommand
        return invokeCommand("clean_export_data_dir", "{}")
    }

//    fun androidSupportService(): AndroidSupportService {
//        return androidSupportService
//    }

    fun createKdbx(fd: ULong, args: String): ApiResponse {
        return androidSupportServiceExtra.createKdbx(fd, args)
    }

    fun saveKdbx(
        fd: ULong,
        fullFileName: String,
        fileName: String,
        overwrite: Boolean
    ): ApiResponse {
        val fileArgs =
            onekeepass.mobile.ffi.FileArgs.FileDecriptorWithFullFileName(fd, fullFileName, fileName)
        return onekeepass.mobile.ffi.saveKdbx(fileArgs, overwrite)
    }

    fun completeSaveAsOnError(
        fileDescriptor: ULong,
        oldFullFileNameUri: String,
        newFullFileNameUri: String,
        fileName: String
    ): ApiResponse {
        return androidSupportServiceExtra.completeSaveAsOnError(
            fileDescriptor,
            oldFullFileNameUri,
            newFullFileNameUri,
            fileName
        )
    }

    fun verifyDbFileChecksum(fd: ULong, fullFileName: String): ApiResponse {
        val fileArgs =
            onekeepass.mobile.ffi.FileArgs.FileDecriptorWithFullFileName(fd, fullFileName, "")
        return onekeepass.mobile.ffi.verifyDbFileChecksum(fileArgs)
    }

    fun writeToBackupOnError(fullFileName: String) {

        val r = onekeepass.mobile.ffi.writeToBackupOnError(fullFileName)
        when (r) {
            is ApiResponse.Success -> {
                Log.d(TAG, "writeToBackupOnError call is successful")
            }

            is ApiResponse.Failure -> {
                Log.e(TAG, "writeToBackupOnError call failed with error ${r.result}")
            }
        }
    }

    fun readKdbx(fd: ULong, args: String): ApiResponse {
        val fileArgs = onekeepass.mobile.ffi.FileArgs.FileDecriptor(fd)
        return onekeepass.mobile.ffi.readKdbx(fileArgs, args)
    }

    fun readKdbx(fullFileName: String, args: String): ApiResponse {
        val fileArgs = onekeepass.mobile.ffi.FileArgs.FullFileName(fullFileName)
        return onekeepass.mobile.ffi.readKdbx(fileArgs, args)
    }

    fun copyPickedKeyFile(fd: ULong, fullFileName: String, fileName: String): String {
        val fileArgs =
            onekeepass.mobile.ffi.FileArgs.FileDecriptorWithFullFileName(fd, fullFileName, fileName)
        return onekeepass.mobile.ffi.copyPickedKeyFile(fileArgs)
    }

    fun uploadAttachment(
        fd: ULong,
        fullFileName: String,
        fileName: String,
        jsonArgs: String
    ): String {
        val fileArgs =
            onekeepass.mobile.ffi.FileArgs.FileDecriptorWithFullFileName(fd, fullFileName, fileName)
        return onekeepass.mobile.ffi.uploadAttachment(fileArgs, jsonArgs)
    }

    fun handlePickedFile(
        fd: ULong,
        fullFileName: String,
        fileName: String,
        jsonArgs: String
    ): String {
        val fileArgs =
            onekeepass.mobile.ffi.FileArgs.FileDecriptorWithFullFileName(fd, fullFileName, fileName)
        return onekeepass.mobile.ffi.handlePickedFile(fileArgs, jsonArgs)
    }

    fun formJsonWithFileName(fullFileName: String): String {
        return jsonService.formWithFileName(fullFileName)
    }

    fun jsonService(): JsonService {
        return jsonService
    }
}

class CommonDeviceServiceImpl(val reactContext: ReactApplicationContext) : CommonDeviceService {
    override fun appHomeDir(): String {
        return reactContext.filesDir.absolutePath
    }

    override fun appGroupHomeDir(): String? {
        // This is not implemented as it is iOS specific
        // Need to move to move iOS specific api callback?
        return null
    }

    override fun cacheDir(): String {
        return reactContext.cacheDir.absolutePath
    }

    override fun tempDir(): String {
        return reactContext.cacheDir.absolutePath
    }

    override fun loadLanguageTranslation(languageId: String): String? {
        var fileContent: String? = null

        val assetManager = reactContext.assets
        val builder = StringBuilder()
        val fileName =
            builder.append("Translations").append("/").append(languageId).append(".json").toString()
        try {
            fileContent = assetManager.open(fileName).bufferedReader().use {
                it.readText()
            }
            //Log.d(TAG, "JSon File content is ${fileContent}")
        } catch (e: Exception) {
            Log.e(TAG, "Translation resource ${fileName} loading failed with exception ${e}")
        }
        return fileContent
    }

    // Kotlin does not have checked exceptions
    // May throw an exception
    override fun loadResourceWordlist(wordlistFileName: String): String {
        var fileContent: String? = null

        val assetManager = reactContext.assets
        val builder = StringBuilder()
        val fileName =
            builder.append("wordlists").append("/").append(wordlistFileName).append(".txt").toString()

        fileContent = assetManager.open(fileName).bufferedReader().use {
            it.readText()
        }
        return fileContent
    }

    override fun uriToFileName(fullFileNameUri: String): String? {
        try {
            val uri = Uri.parse(fullFileNameUri);
            val fs = FileUtils.getMetaInfo(reactContext.contentResolver, uri);
            return fs?.filename
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    override fun uriToFileInfo(fullFileNameUri: String): FileInfo? {
        var info = FileInfo(null, null, null, null)
        var location: String? = null
        try {
            val uri = Uri.parse(fullFileNameUri);
            location = onekeepass.mobile.ffi.extractFileProvider(fullFileNameUri)
            // fullFileNameUri may be stale or non existence or requires user should have
            // opened explicitly; Otherwise SecurityException is thrown.
            // Such exception needs to be caught. If not, as 'uriToFileInfo' is called from
            // rust side, any exception may result in rust panic in FFI layer
            val fs = FileUtils.getMetaInfo(reactContext.contentResolver, uri);

            info.fileName = fs?.filename
            info.fileSize = fs?.size
            // Timestamp when a document was last modified, in milliseconds since January 1, 1970 00:00:00.0 UTC
            info.lastModified = fs?.lastModifiedTime
            info.location = location
            return info
        } catch (e: SecurityException) {
            // This will happen, if we try to create the kdbx file without proper read and write permissions
            // See DocumentPickerServiceModule how these permissions are set while selecting the file.
            Log.e(TAG, "SecurityException due to in sufficient permission")
            info.location = location
            return info

        } catch (e: FileNotFoundException) {
            // Need to add logic in UI layer to handle this
            Log.e(TAG, "Error in createKdbx ${e}")
            // e.printStackTrace()
            info.location = location
            return info

        } catch (e: Exception) {
            // e.printStackTrace()
            info.location = location
            return info
        }
    }
}