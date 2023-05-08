package com.onekeepassmobile

import android.net.Uri
import android.util.Log
import com.facebook.react.bridge.ReactApplicationContext
import onekeepass.mobile.ffi.*
import onekeepass.mobile.ffi.invokeCommand as dbServiceFFIInvokeCommand;
import onekeepass.mobile.ffi.ApiResponse
import java.io.FileNotFoundException

/**
 *  All ffi api calls go through this
 */
private const val TAG = "DbServiceAPI";
object DbServiceAPI {

    private var jsonService = onekeepass.mobile.ffi.JsonService();

    init {
        Log.d(TAG, "Before calling dbServiceEnableLogging")
        dbServiceEnableLogging();
        Log.d(TAG, "After calling dbServiceEnableLogging")
    }

    fun initialize(reactContext: ReactApplicationContext) {
        dbServiceInitialize(CommonDeviceServiceImpl(reactContext))
    }

    fun invokeCommand(commandName: String, args: String): String {
        return dbServiceFFIInvokeCommand(commandName, args)
    }

    fun cleanExportDataDir(): String {
        // Delegates to the invokeCommand
        return invokeCommand("clean_export_data_dir","{}")
    }

    fun createKdbx(fd: ULong, args: String): ApiResponse {
        val fileArgs = onekeepass.mobile.ffi.FileArgs.FileDecriptor(fd)
        return onekeepass.mobile.ffi.createKdbx(fileArgs, args)
    }

    fun saveKdbx(fd: ULong, fullFileName: String, fileName: String): ApiResponse {
        val fileArgs = onekeepass.mobile.ffi.FileArgs.FileDecriptorWithFullFileName(fd, fullFileName, fileName)
        return onekeepass.mobile.ffi.saveKdbx(fileArgs)
    }

    fun readKdbx(fd: ULong, args: String): ApiResponse {
        val fileArgs = onekeepass.mobile.ffi.FileArgs.FileDecriptor(fd)
        return onekeepass.mobile.ffi.readKdbx(fileArgs, args)
    }

    fun readKdbx(fullFileName: String, args: String): ApiResponse {
        val fileArgs = onekeepass.mobile.ffi.FileArgs.FullFileName(fullFileName)
        return onekeepass.mobile.ffi.readKdbx(fileArgs, args)
    }

    fun formJsonWithFileName(fullFileName: String): String {
        return jsonService.formWithFileName(fullFileName)
    }
}

class CommonDeviceServiceImpl(val reactContext: ReactApplicationContext) : CommonDeviceService {
    override fun appHomeDir(): String {
        return reactContext.filesDir.absolutePath
    }

    override fun uriToFileName(fullFileNameUri: String): String? {
        try {
            val uri = Uri.parse(fullFileNameUri);
            val fs = FileUtils.getMetaInfo(reactContext.contentResolver, uri);
            return fs?.filename
        }
        catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    override fun uriToFileInfo(fullFileNameUri: String): FileInfo? {
        var info = FileInfo(null,null,null,null)
        var location:String? = null
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
        }
        catch (e: SecurityException) {
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