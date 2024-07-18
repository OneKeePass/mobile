package com.onekeepassmobile.autofill

import android.R
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.content.IntentSender
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillContext
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.util.Log
import android.view.autofill.AutofillId
import android.widget.RemoteViews

class AuthenticatedAutofillService : AutofillService() {
    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        // Get the structure from the request
        val context: List<FillContext> = request.fillContexts
        val structure: AssistStructure = context[context.size - 1].structure

        // Gets the package name from the calling activity
        val packageName: String = structure.activityComponent.packageName
        Log.d(TAG, "Package name of activity triggered autofill ${packageName}")

        // When our app's form with 'rnp-text-input' shown, the Autofill service is triggered for our app (why?)
        if (packageName == "com.onekeepassmobile") {
            //callback.onFailure("No user and password hints are found")
            Log.d(TAG,"Autofill is called for our app input field and fill request is skipped")
            callback.onSuccess(null)
            return
        }

        cancellationSignal.setOnCancelListener { Log.w(TAG, "Cancel autofill not implemented in OneKeePass yet.") }

        // Traverse the structure looking for nodes to fill out

        val parsedStructure: ParseResult = ViewAutofillParser().traverseStructure(structure)//parseStructure(structure)

        var b = java.util.ArrayList<AutofillId>()

        if (parsedStructure.usernameId != null) {
            b.add(parsedStructure.usernameId!!)
        }

        if (parsedStructure.passwordId != null) {
            b.add(parsedStructure.passwordId!!)
        }

        if (b.isEmpty()) {
            //callback.onFailure("No user and password hints are found")
            Log.d(TAG,"Autofill is called but no valid autofill ids are found and fill request is skipped")
            callback.onSuccess(null)
            return
        } else {
            // Found valid autofill ids and needs to launch the Auth Activity
            this.auth(b, callback)
        }
    }

    fun auth(autofillIds: ArrayList<AutofillId>, callback: FillCallback) {

        Log.d(TAG, "Authentication will be triggered")

//        val authPresentation = RemoteViews(packageName, R.layout.simple_list_item_1).apply {
//            setTextViewText(android.R.id.text1, "OneKeePass Login")
//        }

        val authPresentation = RemoteViewsHelper.overlayPresentation(packageName)

        Log.d(TAG, "authPresentation is built")

        /*
        val authIntent = Intent(this,AutofillAuthenticationActivity::class.java).apply {
            // Send any additional data required to complete the request
            putExtra("AF_LOGIN_DATA_SET", "AF_LOGIN_DATA_SET")
        }
        */

        val authIntent = Intent("com.onekeepass.action.AF_LOGIN").apply {
            // Send any additional data required to complete the request
            putExtra("AF_LOGIN_DATA_SET", "AF_LOGIN_DATA_SET")
        }

        Log.d(TAG, "authIntent is built")

        val pint = PendingIntent.getActivity(
                this,
                1001,
                authIntent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        Log.d(TAG, "PendingIntent is built")

        val intentSender: IntentSender =pint.intentSender

        Log.d(TAG, "intentSender is built")

        Log.d(TAG, "Preparing autofillIds ${autofillIds} and $autofillIds.toTypedArray()")
        // Build a FillResponse object that requires authentication
        val fillResponse: FillResponse = FillResponse.Builder()
                .setAuthentication(autofillIds.toTypedArray(), intentSender, authPresentation)
                .build()

        Log.d(TAG, "Calling onSuccess for authentication")
        callback.onSuccess(fillResponse)


//        val authIntent = Intent("com.onekeepass.action.AF_LOGIN").apply {
//            // Send any additional data required to complete the request
//            putExtra("AF_LOGIN_DATA_SET", "AF_LOGIN_DATA_SET")
//        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        Log.d(TAG, "onSaveRequest is called ")
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "=== Autofill onCreate is called ")
    }

    override fun onConnected() {
        super.onConnected()
        Log.d(TAG, "=== Autofill onConnected is called ")
    }

    override fun onDisconnected() {
        super.onDisconnected()
        Log.d(TAG, "=== Autofill onDisconnected is called ")
    }

    override fun onTimeout(startId: Int) {
        super.onTimeout(startId)
        Log.d(TAG, "=== Autofill onTimeout is called with id $startId")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "=== Autofill onDestroy is called ")
    }



    companion object {
        private val TAG = "OkpAF"
    }
}

