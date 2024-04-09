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

    private var jsonService = onekeepass.mobile.ffi.JsonService();
    private var androidSupportService = onekeepass.mobile.ffi.AndroidSupportService();

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
            dbServiceInitialize(
                    CommonDeviceServiceImpl(reactContext),
                    SecureKeyOperationImpl(reactContext),
                    BackendEventDispatcher(reactContext)
            )
            initialized = true;
        } else {
            Log.d(TAG,"API initialize is alredy done")
        }
    }

    fun invokeCommand(commandName: String, args: String): String {
        return dbServiceFFIInvokeCommand(commandName, args)
    }

    fun cleanExportDataDir(): String {
        // Delegates to the invokeCommand
        return invokeCommand("clean_export_data_dir", "{}")
    }

    fun androidSupportService(): AndroidSupportService {
        return androidSupportService
    }

    fun createKdbx(fd: ULong, args: String): ApiResponse {
        return androidSupportService.createKdbx(fd, args)
    }

    fun saveKdbx(fd: ULong, fullFileName: String, fileName: String, overwrite: Boolean): ApiResponse {
        val fileArgs = onekeepass.mobile.ffi.FileArgs.FileDecriptorWithFullFileName(fd, fullFileName, fileName)
        return onekeepass.mobile.ffi.saveKdbx(fileArgs, overwrite)
    }

    fun completeSaveAsOnError(fileDescriptor: ULong, oldFullFileNameUri: String, newFullFileNameUri: String): ApiResponse {
        return androidSupportService.completeSaveAsOnError(fileDescriptor, oldFullFileNameUri, newFullFileNameUri)
    }

    fun verifyDbFileChecksum(fd: ULong, fullFileName: String): ApiResponse {
        val fileArgs = onekeepass.mobile.ffi.FileArgs.FileDecriptorWithFullFileName(fd, fullFileName, "")
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
        val fileArgs = onekeepass.mobile.ffi.FileArgs.FileDecriptorWithFullFileName(fd, fullFileName, fileName)
        return onekeepass.mobile.ffi.copyPickedKeyFile(fileArgs)
    }

    fun uploadAttachment(fd: ULong, fullFileName: String, fileName: String, jsonArgs: String): String {
        val fileArgs = onekeepass.mobile.ffi.FileArgs.FileDecriptorWithFullFileName(fd, fullFileName, fileName)
        return onekeepass.mobile.ffi.uploadAttachment(fileArgs, jsonArgs)
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

    override fun cacheDir(): String {
        return reactContext.cacheDir.absolutePath
    }

    override fun tempDir(): String {
        return reactContext.cacheDir.absolutePath
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