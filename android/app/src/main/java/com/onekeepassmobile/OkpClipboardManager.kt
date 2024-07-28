package com.onekeepassmobile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.os.persistableBundleOf
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import onekeepass.mobile.ffi.AppClipboardCopyData
import java.util.concurrent.TimeUnit


object OkpClipboardManager {

    private val TAG = "OkpClipboardManager"

    private lateinit var clipboardManager: ClipboardManager

    fun setClipboardManager(context:Context) {
        clipboardManager = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        Log.d(TAG,"The clipboardManager is created")
    }

    fun setText(clipDataArg: AppClipboardCopyData) {
        val context = MainApplication.getInstanceContext()

        clipboardManager.setPrimaryClip(
                ClipData
                        .newPlainText("", clipDataArg.fieldValue)
                        .apply {
                            description.extras = persistableBundleOf(
                                    "android.content.extra.IS_SENSITIVE" to clipDataArg.protected,
                            )
                        },
        )

        // if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
        //     val descriptor = clipDataArg.fieldName
        //     Toast.makeText(context,descriptor, Toast.LENGTH_SHORT, ).show()
        // }

        // cleanupAfter 0 sec means no timeout 
        if (clipDataArg.cleanupAfter.toInt() == 0 ) return

        // Clipboard is cleared after 'clipDataArg.cleanupAfter' seconds
        // This happens even if the app is closed or activity is paused
        val clearClipboardRequest: OneTimeWorkRequest =
                OneTimeWorkRequest.Builder(ClearClipboardWorker::class.java)
                        .setInitialDelay(clipDataArg.cleanupAfter.toLong(), TimeUnit.SECONDS).build()

        WorkManager.getInstance(context).enqueueUniqueWork(
                "ClearClipboard",
                ExistingWorkPolicy.REPLACE,
                clearClipboardRequest,
        )
    }
}

class ClearClipboardWorker(appContext: Context, workerParams: WorkerParameters,): Worker(appContext, workerParams) {

    private val clipboardManager = appContext.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager

    override fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboardManager.clearPrimaryClip()
        }
        return Result.success()
    }

}