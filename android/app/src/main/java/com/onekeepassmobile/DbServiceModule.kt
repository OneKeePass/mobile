package com.onekeepassmobile

import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.facebook.react.bridge.*
import com.onekeepassmobile.passkey.PasskeyRequestStore
import onekeepass.mobile.ffi.ApiResponse
import java.io.FileNotFoundException
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import org.json.JSONObject


private const val TAG = "DbServiceModule"  //TAG can only be max 23 characters

class DbServiceModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {
    private val contentResolver = reactContext.contentResolver
    private val executorService: ExecutorService = Executors.newFixedThreadPool(4)

    // issue #19: one-thread scheduler used only to time out a blocking
    // ContentResolver.refresh() call (see refreshCloudDocumentBeforeRead).
    private val refreshTimeoutScheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor()
    private val biometricService: BiometricService = BiometricService(reactContext)
    override fun getName() = "OkpDbService"

    // IMPORTANT: This call should have been made before any API Calls
    init {
        Log.d(TAG, "Called initialize with reactContext $reactContext")
        DbServiceAPI.initialize(reactContext);
        EventEmitter.initialize(reactContext)
    }

    fun getLocale(): Locale {
//        val p = getReactApplicationContext().getSharedPreferences("react-native", Context.MODE_PRIVATE)
//        val s = p.getString("locale_override", null);
//        Log.d(TAG,"p is ${p} , s is ${s}" )

        val locale = getReactApplicationContext().getResources().getConfiguration().locale
        Log.d(TAG, "current country is ${locale.country}, language ${locale.language} ")
        return locale
    }

    override fun getConstants(): MutableMap<String, String?> {

        val locale = getLocale()
        val sdCardDir = try {
            // Search via env may not be reliable. Recent Android versions
            // discourage/restrict full access to public locations.
            System.getenv("SECONDARY_STORAGE") ?: System.getenv("EXTERNAL_STORAGE")
        } catch (e: Throwable) {
            null
        }

        return hashMapOf(
            "CacheDir" to reactApplicationContext.cacheDir.absolutePath,
            "DatabaseDir" to reactApplicationContext.getDatabasePath("FileAccessProbe").parent,
            "DocumentDir" to reactApplicationContext.filesDir.absolutePath,
            "MainBundleDir" to reactApplicationContext.applicationInfo.dataDir,
            "SDCardDir" to sdCardDir,
            "Country" to locale.country,  // Device country
            "Language" to locale.language, // Device level language
            "BiometricAvailable" to biometricService.biometricAuthenticationAvailbale().toString(),

            // See https://developer.android.com/reference/android/os/Build.VERSION
            "AndroidRelease" to Build.VERSION.RELEASE,
            "AndroidSdkApi" to Build.VERSION.SDK_INT.toString(),
            )
    }

    // UI layer needs to call to see if the app is opened by pressing a .kdbx file and
    // if that is the case, show the login dialog accordingly with the available uri of the intent
    @ReactMethod
    fun kdbxUriToOpenOnCreate(promise: Promise) {
        promise.resolve(EventEmitter.kdbxUriToOpenOnCreate())
    }

    // UI layer needs to call to see if the app is opened by pressing an 'otpauth://' link -
    // typically the one shown by the device Camera app after scanning a 2FA QR code
    @ReactMethod
    fun otpAuthUrlOnCreate(promise: Promise) {
        promise.resolve(EventEmitter.otpAuthUrlOnCreate())
    }

    // IMPORTANT:
    // Need to use explicit background thread to call all the backend apis so that UI thread can be
    // released and otherwise UI thread will be blocked
    // See invokeCommand,readKdbx,...etc

    // See https://stackoverflow.com/questions/36330551/why-is-this-call-to-a-react-native-native-module-blocking-the-ui

    @ReactMethod
    fun invokeCommand(commandName: String, args: String, promise: Promise) {
        executorService.execute {
            Log.d(TAG, "invokeCommand is called with command name $commandName and args $args")
            val response = DbServiceAPI.invokeCommand(commandName, args)
            promise.resolve(response)
        }
    }

    // Calls any android specific invoke commands
    @ReactMethod
    fun androidInvokeCommand(commandName: String, args: String, promise: Promise) {
        executorService.execute {
            Log.d(
                TAG,
                "androidInvokeCommand is called with command name $commandName and args $args"
            )
            val response = DbServiceAPI.androidInvokeCommand(commandName, args)
            promise.resolve(response)
        }
    }

    @ReactMethod
    fun readKdbx(fullFileNameUri: String, args: String, promise: Promise) {
        Log.d(TAG, "readKdbx is called with fullFileNameUri $fullFileNameUri  ")
        executorService.execute {
            val uri = Uri.parse(fullFileNameUri);
            try {
                if (fullFileNameUri.startsWith("content://")) {
                    // issue #19: nudge cloud providers (e.g. OneDrive) to sync the
                    // document before we read, so opening from the recent list does not
                    // return a stale cached copy. Safe no-op for local files.
                    refreshCloudDocumentBeforeRead(uri)
                    val fd: ParcelFileDescriptor? = contentResolver.openFileDescriptor(uri, "r");
                    //fd will be null if the provider recently crashed
                    if (fd != null) {
                        // detachFd call should be used so that the file is closed in the rust code automatically
                        // However in CreateKdbx and saveKdbx, kotlin side is responsible for closing the file
                        val response = DbServiceAPI.readKdbx(fd.detachFd().toULong(), args)
                        // Track the open db key so PasskeyProviderService can find matching passkeys
                        if (response is ApiResponse.Success) {
                            try {
                                if (!JSONObject(response.result).has("error")) {
                                    PasskeyRequestStore.currentDbKey = fullFileNameUri
                                }
                            } catch (_: Exception) {}
                        }
                        resolveResponse(response, promise)
                        // Log.d(TAG, "File created using fd with response $response")
                    } else {
                        // Do we need to ask the user select the kdbx again to read ?
                        promise.reject(E_READ_FIE_DESCRIPTOR_ERROR, "Invalid file descriptor")
                    }
                } else {
                    // Here the url is the full internal file path from the app sandbox itself
                    // At this time, all db files are used from the storages that are external to this app
                    // and accordingly those uris always start with content:// and this is not used
                    Log.i(
                        TAG,
                        "API readKdbx is called  with Internal file path fullFileNameUri $fullFileNameUri  "
                    )
                    resolveResponse(DbServiceAPI.readKdbx(fullFileNameUri, args), promise)
                }

            } catch (e: SecurityException) {
                // UI layer needs to handle this with appropriate message to the user
                // This will happen, if we try to read the kdbx file without proper permissions
                // We need to obtain while selecting the file.
                // See 'pickKdbxFileToOpen'
                Log.e(TAG, "SecurityException due to in sufficient permission")
                promise.reject(E_PERMISSION_REQUIRED_TO_READ, e)
            } catch (e: FileNotFoundException) {
                // Need to add logic in UI layer to handle this
                // e.printStackTrace()
                Log.e(TAG, "Error in readKdbx ${e}")
                promise.reject(E_FILE_NOT_FOUND, e)
            } catch (e: Exception) {
                // e.printStackTrace()
                Log.e(TAG, "Error in readKdbx ${e}")
                promise.reject(E_READ_CALL_FAILED, e)
            }
        }
    }

    @ReactMethod
    fun saveKdbx(fullFileNameUri: String, overwrite: Boolean, promise: Promise) {
        executorService.execute {
            val uri = Uri.parse(fullFileNameUri);
            try {
                if (!overwrite && (verifyDbFileChanged(fullFileNameUri, promise))) {
                    Log.d(TAG, "Db contents have changed and saving is not done")
                    // Store the db file with changed data to backup for later offline use
                    DbServiceAPI.writeToBackupOnError(fullFileNameUri)
                    return@execute
                }
                Log.d(TAG, "Db contents have not changed and continuing with saving")

                // Here it is assumed the fileNameUri starts with content:// for which there will be
                // a resolver. If we use internal file path, we need to do something similar to readKdbx

                val fileName = FileUtils.getMetaInfo(contentResolver, uri)?.filename ?: ""
                val fd: ParcelFileDescriptor? = contentResolver.openFileDescriptor(uri, "rwt");
                //null if the provider recently crashed
                if (fd != null) {
                    // fd.detachFd() is not used so that Save works without any issues in case of
                    // google drive
                    when (val response = DbServiceAPI.saveKdbx(
                        fd.fd.toULong(),
                        fullFileNameUri,
                        fileName,
                        overwrite
                    )) {
                        is ApiResponse.Success -> {
                            //Log.d(TAG, "File created using fd with SUCCESS response ${response.result}")
                            promise.resolve(response.result)
                        }

                        is ApiResponse.Failure -> {
                            //Log.d(TAG, "File created using fd with FAILURE response $response.result")
                            promise.resolve(response.result)
                        }
                    }
                    // IMPORTANT: The caller is responsible for closing as the file is not yet closed
                    fd.close()
                    // Log.d(TAG, "File created using fd with response $response")
                } else {
                    // Do we need to ask the user select the kdbx again to read ?
                    promise.reject(E_SAVE_FIE_DESCRIPTOR_ERROR, "Invalid file descriptor")
                }
            } catch (e: SecurityException) {
                // Need to add logic in UI layer to handle this
                // This will happen, if we try to read the kdbx file without proper permissions
                // We need to obtain while selecting the file.
                Log.e(TAG, "SecurityException due to in sufficient permission")
                //TODO: Store the db file with changed data to backup for later offline use
                DbServiceAPI.writeToBackupOnError(fullFileNameUri)
                promise.reject(E_PERMISSION_REQUIRED_TO_WRITE, e)
            } catch (e: FileNotFoundException) {
                // e.printStackTrace()
                // Need to add logic in UI layer to handle this
                Log.e(TAG, "Error in saveKdbx ${e}")
                //TODO: Store the db file with changed data to backup for later offline use
                DbServiceAPI.writeToBackupOnError(fullFileNameUri)
                promise.reject(E_FILE_NOT_FOUND, e)
            } catch (e: Exception) {
                // e.printStackTrace()
                Log.e(TAG, "Error in saveKdbx ${e}")
                //TODO: Store the db file with changed data to backup for later offline use
                DbServiceAPI.writeToBackupOnError(fullFileNameUri)
                promise.reject(E_SAVE_CALL_FAILED, e)
            }
        }
    }

    @ReactMethod
    fun createKdbx(fileNameUri: String, args: String, promise: Promise) {

        executorService.execute {
            Log.i(TAG, "Received fullFileName is $fileNameUri")
            val uri = Uri.parse(fileNameUri);
            Log.i(TAG, "Parsed uri is $uri")
            try {
                // Here it is assumed the fileNameUri starts with content:// for which there will be
                // a resolver. If we use internal file path, we need to do something similar to readKdbx

                // See rational to use mode:rwt here https://issuetracker.google.com/issues/180526528
                // Our requirement is at least "rw"
                val fd: ParcelFileDescriptor? = contentResolver.openFileDescriptor(uri, "rwt");
                // Log.d(TAG, "After FileDescriptor.${fd}")
                if (fd != null) {
                    /*
                    // If we use detachFd call, then the file is closed in the rust code automatically
                    // See below why we should not use for createKdbx call
                    val response = DbServiceAPI.createKdbx(fd.detachFd().toULong(), args);
                    resolveResponse(response, promise)
                    */

                    // Rust side the file should not be closed. So fd.detachFd() is not used.
                    // if we use fd.detachFd(), then 'create db file' for google drive did not work
                    val response = DbServiceAPI.createKdbx(fd.fd.toULong(), args);
                    resolveResponse(response, promise)
                    // IMPORTANT:
                    // The caller is responsible for closing the file using its file descriptor
                    // as the file is not yet closed
                    fd.close()

                } else {
                    // Do we need to ask the user select the kdbx again to read ?
                    promise.reject(
                        E_CREATE_FIE_DESCRIPTOR_ERROR,
                        "Invalid file descriptor in createKdbx"
                    )
                }
            } catch (e: SecurityException) {
                // This will happen, if we try to create the kdbx file without proper read and write permissions
                // We need to obtain while selecting the file. See DocumentPickerServiceModule
                Log.e(TAG, "SecurityException due to in sufficient permission")
                promise.reject(E_PERMISSION_REQUIRED_TO_WRITE, e)
            } catch (e: FileNotFoundException) {
                // Need to add logic in UI layer to handle this
                Log.e(TAG, "Error in createKdbx ${e}")
                // e.printStackTrace()
                promise.reject(E_FILE_NOT_FOUND, e)
            } catch (e: Exception) {
                // e.printStackTrace()
                Log.e(TAG, "Error in createKdbx ${e}")
                promise.reject(E_CREATE_CALL_FAILED, e)
            }
        }
    }

    @ReactMethod
    fun completeSaveAsOnError(
        oldFullFileNameUri: String,
        newFullFileNameUri: String,
        fileName: String,
        promise: Promise
    ) {
        executorService.execute {
            Log.i(TAG, "Received fullFileName is $newFullFileNameUri")
            val uri = Uri.parse(newFullFileNameUri);
            Log.i(TAG, "Parsed uri is $uri")
            try {
                // See rational to use mode:rwt here https://issuetracker.google.com/issues/180526528
                // Our requirement is at least "rw"
                val fd: ParcelFileDescriptor? = contentResolver.openFileDescriptor(uri, "rwt");
                if (fd != null) {
                    // Rust side the file should not be closed. So fd.detachFd() is not used.
                    // if we use fd.detachFd(), then 'create db file' for google drive did not work
                    val response = DbServiceAPI.completeSaveAsOnError(
                        fd.fd.toULong(),
                        oldFullFileNameUri,
                        newFullFileNameUri,
                        fileName
                    );
                    resolveResponse(response, promise)
                    // IMPORTANT:
                    // The caller is responsible for closing the file using its file descriptor
                    // as the file is not yet closed
                    fd.close()
                } else {
                    // Do we need to ask the user select the kdbx again to read ?
                    promise.reject(
                        E_CREATE_FIE_DESCRIPTOR_ERROR,
                        "Invalid file descriptor in createKdbx"
                    )
                }
            } catch (e: SecurityException) {
                // This will happen, if we try to create the kdbx file without proper read and write permissions
                // We need to obtain while selecting the file. See DocumentPickerServiceModule
                Log.e(TAG, "SecurityException due to in sufficient permission")
                promise.reject(E_PERMISSION_REQUIRED_TO_WRITE, e)
            } catch (e: FileNotFoundException) {
                // Need to add logic in UI layer to handle this
                Log.e(TAG, "Error in completeSaveAsOnError ${e}")
                // e.printStackTrace()
                promise.reject(E_FILE_NOT_FOUND, e)
            } catch (e: Exception) {
                // e.printStackTrace()
                Log.e(TAG, "Error in completeSaveAsOnError ${e}")
                promise.reject(E_SAVE_AS_CALL_FAILED, e)
            }
        }
    }

    @ReactMethod
    fun authenticateWithBiometric(promise: Promise) {
        // BiometricPrompt should be called in the UI thread
        UiThreadUtil.runOnUiThread({
            val executor = Executors.newSingleThreadExecutor()
            Log.d(TAG, "Calling showPrompt....")
            biometricService.showPrompt(reactApplicationContext.currentActivity as FragmentActivity, executor, promise)
            Log.d(TAG, "Called showPrompt")
        })

        // BiometricPrompt should be called in the UI thread. The following did not work and threw
        // java.lang.IllegalStateException: Must be called from main thread of fragment host

        // val executor = ContextCompat.getMainExecutor(this.reactApplicationContext)
        // biometricService.showPrompt(currentActivity as FragmentActivity, executor,promise)
    }

    // This is a follow up method that is called after user picks a file using document picker service
    // To be replaced by the generic handlePickedFile
    @ReactMethod
    fun copyKeyFile(fullKeyFileNameUri: String, promise: Promise) {
        Log.d(TAG, "copyKeyFile is called with fullFileNameUri $fullKeyFileNameUri  ")
        executorService.execute {
            val uri = Uri.parse(fullKeyFileNameUri);
            try {
                val fd: ParcelFileDescriptor? = contentResolver.openFileDescriptor(uri, "r");
                val fileName = FileUtils.getMetaInfo(contentResolver, uri)?.filename ?: ""
                //fd will be null if the provider recently crashed
                if (fd != null) {
                    // detachFd call should be used so that the file is closed in the rust code automatically
                    promise.resolve(
                        DbServiceAPI.copyPickedKeyFile(
                            fd.detachFd().toULong(),
                            fullKeyFileNameUri,
                            fileName
                        )
                    )
                } else {
                    promise.reject(E_READ_FIE_DESCRIPTOR_ERROR, "Invalid file descriptor")
                }

            } catch (e: SecurityException) {
                // UI layer needs to handle this with apprpriate message to the user
                // This will happen, if we try to read the kdbx file without proper permissions
                // We need to obtain while selecting the file.
                // See 'pickKdbxFileToOpen'
                Log.e(TAG, "SecurityException due to in sufficient permission")
                promise.reject(E_PERMISSION_REQUIRED_TO_READ, e)
            } catch (e: FileNotFoundException) {
                // Need to add logic in UI layer to handle this
                // e.printStackTrace()
                Log.e(TAG, "Error in copyKeyFile ${e}")
                promise.reject(E_FILE_NOT_FOUND, e)
            } catch (e: Exception) {
                // e.printStackTrace()
                Log.e(TAG, "Error in copyKeyFile ${e}")
                promise.reject(E_READ_CALL_FAILED, e)
            }
        }
    }

    // This is a follow up method that is called after user picks a file using document picker service
    // To be replaced by the generic handlePickedFile
    @ReactMethod
    fun uploadAttachment(fullKeyFileNameUri: String, jsonArgs: String, promise: Promise) {
        Log.d(
            TAG,
            "uploadAttachment is called with fullFileNameUri $fullKeyFileNameUri and jsonArgs $jsonArgs "
        )
        executorService.execute {
            val uri = Uri.parse(fullKeyFileNameUri);
            try {
                val fd: ParcelFileDescriptor? = contentResolver.openFileDescriptor(uri, "r");
                val fileName = FileUtils.getMetaInfo(contentResolver, uri)?.filename ?: ""
                //fd will be null if the provider recently crashed
                if (fd != null) {
                    // detachFd call should be used so that the file is closed in the rust code automatically
                    promise.resolve(
                        DbServiceAPI.uploadAttachment(
                            fd.detachFd().toULong(),
                            fullKeyFileNameUri,
                            fileName,
                            jsonArgs
                        )
                    )
                } else {
                    promise.reject(E_READ_FIE_DESCRIPTOR_ERROR, "Invalid file descriptor")
                }

            } catch (e: SecurityException) {
                // UI layer needs to handle this with appropriate message to the user
                // This will happen, if we try to read the kdbx file without proper permissions
                // We need to obtain while selecting the file.
                // See 'pickKdbxFileToOpen'
                Log.e(TAG, "SecurityException due to in sufficient permission")
                promise.reject(E_PERMISSION_REQUIRED_TO_READ, e)
            } catch (e: FileNotFoundException) {
                // Need to add logic in UI layer to handle this
                // e.printStackTrace()
                Log.e(TAG, "Error in uploadAttachment ${e}")
                promise.reject(E_FILE_NOT_FOUND, e)
            } catch (e: Exception) {
                // e.printStackTrace()
                Log.e(TAG, "Error in uploadAttachment ${e}")
                promise.reject(E_READ_CALL_FAILED, e)
            }
        }
    }

    // Adds a custom icon by reading the user-picked image file via SAF
    // (Android Storage Access Framework) and delegating to the Rust FFI,
    // which normalizes the bytes to a 64x64 PNG and inserts the icon
    // into the loaded DB. Mirrors uploadAttachment / handlePickedFile.
    @ReactMethod
    fun addCustomIconFromFile(fullKeyFileNameUri: String, jsonArgs: String, promise: Promise) {
        Log.d(
            TAG,
            "addCustomIconFromFile is called with fullFileNameUri $fullKeyFileNameUri and jsonArgs $jsonArgs "
        )
        executorService.execute {
            val uri = Uri.parse(fullKeyFileNameUri);
            try {
                val fd: ParcelFileDescriptor? = contentResolver.openFileDescriptor(uri, "r");
                val fileName = FileUtils.getMetaInfo(contentResolver, uri)?.filename ?: ""
                if (fd != null) {
                    promise.resolve(
                        DbServiceAPI.addCustomIconFromFile(
                            fd.detachFd().toULong(),
                            fullKeyFileNameUri,
                            fileName,
                            jsonArgs
                        )
                    )
                } else {
                    promise.reject(E_READ_FIE_DESCRIPTOR_ERROR, "Invalid file descriptor")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException due to in sufficient permission")
                promise.reject(E_PERMISSION_REQUIRED_TO_READ, e)
            } catch (e: FileNotFoundException) {
                Log.e(TAG, "Error in addCustomIconFromFile ${e}")
                promise.reject(E_FILE_NOT_FOUND, e)
            } catch (e: Exception) {
                Log.e(TAG, "Error in addCustomIconFromFile ${e}")
                promise.reject(E_READ_CALL_FAILED, e)
            }
        }
    }

    // This is a follow up method that is called after user picks a file using document picker service
    // TODO: Replace copyKeyFile,uploadAttachment to use this common fn after making required changes in rust side
    @ReactMethod
    fun handlePickedFile(fullKeyFileNameUri: String, jsonArgs: String, promise: Promise) {
        Log.d(
            TAG,
            "The handlePickedFile is called with fullFileNameUri $fullKeyFileNameUri and jsonArgs $jsonArgs "
        )
        executorService.execute {
            val uri = Uri.parse(fullKeyFileNameUri);
            try {
                val fd: ParcelFileDescriptor? = contentResolver.openFileDescriptor(uri, "r");
                val fileName = FileUtils.getMetaInfo(contentResolver, uri)?.filename ?: ""
                //fd will be null if the provider recently crashed
                if (fd != null) {
                    // detachFd call should be used so that the file is closed in the rust code automatically
                    promise.resolve(
                        DbServiceAPI.handlePickedFile(
                            fd.detachFd().toULong(),
                            fullKeyFileNameUri,
                            fileName,
                            jsonArgs
                        )
                    )
                } else {
                    promise.reject(E_READ_FIE_DESCRIPTOR_ERROR, "Invalid file descriptor")
                }

            } catch (e: SecurityException) {
                // UI layer needs to handle this with appropriate message to the user
                // This will happen, if we try to read the kdbx file without proper permissions
                // We need to obtain while selecting the file.
                // See 'pickKdbxFileToOpen'
                Log.e(TAG, "SecurityException due to in sufficient permission")
                promise.reject(E_PERMISSION_REQUIRED_TO_READ, e)
            } catch (e: FileNotFoundException) {
                // Need to add logic in UI layer to handle this
                // e.printStackTrace()
                Log.e(TAG, "Error in uploadAttachment ${e}")
                promise.reject(E_FILE_NOT_FOUND, e)
            } catch (e: Exception) {
                // e.printStackTrace()
                Log.e(TAG, "Error in uploadAttachment ${e}")
                promise.reject(E_READ_CALL_FAILED, e)
            }
        }
    }

    private fun resolveResponse(response: ApiResponse, promise: Promise) {
        when (response) {
            is ApiResponse.Success -> {
                Log.d(TAG, "File created using fd with SUCCESS response ${response.result}")
                promise.resolve(response.result)
            }

            is ApiResponse.Failure -> {
                Log.d(TAG, "File created using fd with FAILURE response $response.result")
                promise.resolve(response.result)
            }
        }
    }

    // issue #19: Ask a SAF document provider to sync the document with its backing
    // store before we open it for reading.
    //
    // Why: OneDrive (and some other cloud providers) return a lazily-synced, possibly
    // stale local copy from openFileDescriptor("r") when a db is opened from the recent
    // list, so the user sees days-old content. ContentResolver.refresh() nudges the
    // provider to pull the current version first. It is best-effort and advisory:
    // providers that don't implement it (or local / Downloads files) simply no-op, so
    // this is safe to call for every content:// uri.
    //
    // This runs on the background executor thread (callers wrap it in
    // executorService.execute { ... }), so it never blocks the UI thread. But refresh()
    // itself blocks while the provider syncs, so we cap it with REFRESH_TIMEOUT_MS: a
    // scheduled task cancels the signal, which aborts refresh(), and we then read with
    // whatever copy the provider already has.
    //
    // EASY DISABLE: flip REFRESH_CLOUD_DOC_BEFORE_READ (companion object) to false to
    // turn this off everywhere. Both call sites then become no-ops with no other change.
    private fun refreshCloudDocumentBeforeRead(uri: Uri) {
        if (!REFRESH_CLOUD_DOC_BEFORE_READ) return
        // ContentResolver.refresh(Uri, Bundle, CancellationSignal) is API 26+; minSdk is 24.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val signal = CancellationSignal()
        // Abort the (blocking) refresh if the provider takes too long to sync.
        val timeoutFuture = refreshTimeoutScheduler.schedule(
            { signal.cancel() }, REFRESH_TIMEOUT_MS, TimeUnit.MILLISECONDS
        )
        try {
            val refreshed = contentResolver.refresh(uri, null, signal)
            Log.d(TAG, "refresh() for $uri returned $refreshed")
        } catch (e: OperationCanceledException) {
            // Timed out via signal.cancel(); read the already-available copy.
            Log.w(TAG, "refresh() timed out after ${REFRESH_TIMEOUT_MS}ms for $uri; reading current copy")
        } catch (e: Exception) {
            // Provider may not support refresh or may reject it; we read regardless.
            Log.w(TAG, "refresh() not honored by provider for $uri: $e")
        } finally {
            // refresh() already returned/threw, so stop the pending cancel.
            timeoutFuture.cancel(false)
        }
    }

    // Will throw exception
    private fun verifyDbFileChanged(fullFileNameUri: String, promise: Promise): Boolean {
        val uri = Uri.parse(fullFileNameUri);
        // issue #19: compare the change-check against the current remote bytes, not a
        // stale cloud cache. Safe no-op for local files / unsupported providers.
        refreshCloudDocumentBeforeRead(uri)
        // Will throw exception
        val fd: ParcelFileDescriptor? = contentResolver.openFileDescriptor(uri, "r");
        if (fd != null) {
            return when (val response =
                DbServiceAPI.verifyDbFileChecksum(fd.detachFd().toULong(), fullFileNameUri)) {
                is ApiResponse.Success -> {
                    //Log.d(TAG, "File created using fd with SUCCESS response ${response.result}")
                    // promise.resolve(response.result)
                    false // no promise call done
                }

                is ApiResponse.Failure -> {
                    Log.d(TAG, "verifyDbFileChanged with FAILURE response $response.result")
                    promise.resolve(response.result)
                    true // resolved
                }
            }
        } else {
            // Do we need to ask the user select the kdbx again to read ?
            promise.reject(E_SAVE_FIE_DESCRIPTOR_ERROR, "Invalid file descriptor")
            return true // resolved
        }
    }

    companion object {
        // issue #19: master switch for the pre-read cloud-document refresh.
        // Set to false to disable refreshCloudDocumentBeforeRead() everywhere (e.g. if
        // the on-device flow misbehaves) without touching the call sites.
        private const val REFRESH_CLOUD_DOC_BEFORE_READ = true

        // issue #19: upper bound on the blocking ContentResolver.refresh() sync before we
        // give up and read the currently-available copy. Tune if cloud syncs need longer.
        private const val REFRESH_TIMEOUT_MS = 10_000L

        private const val E_PERMISSION_REQUIRED_TO_READ = "PERMISSION_REQUIRED_TO_READ"
        private const val E_PERMISSION_REQUIRED_TO_WRITE = "PERMISSION_REQUIRED_TO_WRITE"
        private const val E_READ_CALL_FAILED = "READ_CALL_FAILED"
        private const val E_FILE_NOT_FOUND = "FILE_NOT_FOUND"
        private const val E_CREATE_CALL_FAILED = "CREATE_CALL_FAILED"
        private const val E_SAVE_AS_CALL_FAILED = "SAVE_AS_CALL_FAILED"
        private const val E_SAVE_CALL_FAILED = "SAVE_CALL_FAILED"
        private const val E_CREATE_FIE_DESCRIPTOR_ERROR = "CREATE_FIE_DESCRIPTOR_ERROR"
        private const val E_SAVE_FIE_DESCRIPTOR_ERROR = "SAVE_FIE_DESCRIPTOR_ERROR"
        private const val E_READ_FIE_DESCRIPTOR_ERROR = "READ_FIE_DESCRIPTOR_ERROR"

    }
}