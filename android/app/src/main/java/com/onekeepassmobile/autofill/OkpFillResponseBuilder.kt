package com.onekeepassmobile.autofill

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.graphics.drawable.Icon
import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.util.Log
import android.view.View
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import com.onekeepassmobile.R

@RequiresApi(Build.VERSION_CODES.O)
object OkpFillResponseBuilder {

    private const val TAG = "OkpAF"

    private var parsedAutofillRequestToUse: ParsedAutofillRequest? = null

    fun build(packageContext: Context, parsedAutofillRequest: ParsedAutofillRequest): FillResponse? {

        parsedAutofillRequestToUse = null

        val fillResponseBuilder = FillResponse.Builder()

        return if (parsedAutofillRequest != null) {
            parsedAutofillRequestToUse = parsedAutofillRequest
            Log.d(TAG,"parsedAutofillRequestToUse is set $parsedAutofillRequestToUse")
            val data = buildDataSet(packageContext, parsedAutofillRequest)
            fillResponseBuilder
                    .setIgnoredIds(*parsedAutofillRequest.ignoreAutofillIds.toTypedArray())
                    .addDataset(data)
                    .build()
        } else {
            null
        }
    }

    // Called to complete the autofill with the values of the entry the user picked. Any of
    // username, password and otp may be null - a code only screen sends the otp alone
    fun completeLoginAutofill(username: String?, password: String?, otp: String?) {
        // There should be a valid 'AutofillAuthenticationActivity' instance 
        AutofillAuthenticationActivity.getActivityToComplteFill()?.let { activity ->
            // Builds the response with the filled dataset and completes autofill call 
            val dataset = buildLoginDatasetToFinalAutofill(activity.applicationContext, username, password, otp)
            if (dataset == null) {
                Log.d(TAG, "Filled dataset is null and activity is cancelled")
                activity.setResult(Activity.RESULT_CANCELED)
                activity.finish()
            } else {
                val resultIntent = createAutofillSelectionResultIntent(dataset!!)
                Log.d(TAG, "Filled dataset is valid and activity is OKed")
                activity.setResult(Activity.RESULT_OK, resultIntent)
                activity.finish()
            }
        }
    }

    fun callingAppUri(): String? {
        Log.d(TAG,"Returning uri from parsedAutofillRequestToUse $parsedAutofillRequestToUse")
        return parsedAutofillRequestToUse?.uri
    }

    // Called to fill the picked entry's values on user picks an entry
    private fun buildLoginDatasetToFinalAutofill(context: Context, username: String?, password: String?, otp: String?): Dataset? {

        if (username == null && password == null && otp == null) {
            return null
        }

        if (parsedAutofillRequestToUse?.parsedRequestDataSet?.views == null) {
            return null
        }

        // Remove this as we do not require to create Dataset.Builder with this during filled call
        val remoteViews = RemoteViewsHelper.overlayPresentation(context.packageName)

        var builder = Dataset.Builder()

        val loginAutoviewFields = parsedAutofillRequestToUse?.parsedRequestDataSet?.views?.filterIsInstance<AutofillView.Login>()

        loginAutoviewFields?.let {
            for (e in it) {
                val afvalue = when (e) {
                    is AutofillView.Login.Username -> {
                        buildAutofillValue(e, username)
                    }

                    is AutofillView.Login.Password -> {
                        buildAutofillValue(e, password)
                    }
                }
                builder = builder.setValue(e.data.autofillId, afvalue)
            }
        }

        // The 2FA code field, either the only field of a code only screen or the extra
        // field of a combined login form
        for (e in totpViewsToFill()) {
            builder = builder.setValue(e.data.autofillId, buildAutofillValue(e, otp))
        }

        return builder.build()
    }

    // The 2FA code fields of the pending request, from either dataset kind
    private fun totpViewsToFill(): List<AutofillView.Totp> =
            when (val dataSet = parsedAutofillRequestToUse?.parsedRequestDataSet) {
                is ParsedRequestDataSet.Totp -> dataSet.views
                is ParsedRequestDataSet.Login -> listOfNotNull(dataSet.totpView)
                null -> emptyList()
            }

    // Whether the pending request has a 2FA code field to fill. The ClojureScript side uses
    // this to decide whether to generate a token for the entry the user picks
    fun hasTotpField(): Boolean = totpViewsToFill().isNotEmpty()

    // Whether the pending request is for a 2FA code alone (no credential field). The entry
    // list is then filtered to entries that can produce a code
    fun isTotpOnlyRequest(): Boolean =
            parsedAutofillRequestToUse?.parsedRequestDataSet is ParsedRequestDataSet.Totp

    // An intent to use with autofill activity complete call
    private fun createAutofillSelectionResultIntent(dataset: Dataset): Intent =
            Intent().apply { putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset) }

    // Used to build the dataset when user presses 'OneKeePass Login'
    private fun buildDataSet(packageContext: Context, parsedAutofillRequest: ParsedAutofillRequest): Dataset {

        val authPresentation = RemoteViewsHelper.overlayPresentation(packageContext.packageName)
        val authIntent = Intent("com.onekeepass.action.AF_LOGIN").apply {
            // Send any additional data required to complete the request
            putExtra("AF_LOGIN_DATA_SET", "AF_LOGIN_DATA_SET")
        }

        val pendingIntent = PendingIntent.getActivity(
                packageContext,
                1001,
                authIntent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val intentSender: IntentSender = pendingIntent.intentSender

        var builder = Dataset.Builder(authPresentation).setAuthentication(intentSender)

        Log.d(TAG, "parsedAutofillRequest.parsedRequestDataSet is $parsedAutofillRequest.parsedRequestDataSet")

        val maxInlineSuggestionsCount = (parsedAutofillRequest.maxInlineSuggestionsCount - 1).coerceAtMost(maximumValue = 5)

        // keyboards and other input-method editors (IMEs) can display autofill suggestions inline, in a suggestion strip
        //https://developer.android.com/identity/autofill/ime-autofill#configure-provider
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val inlinePresentationSpec = parsedAutofillRequest.inlinePresentationSpecs?.last()
            inlinePresentationSpec?.let {
                val subtitleRes = if (parsedAutofillRequest.parsedRequestDataSet is ParsedRequestDataSet.Totp) {
                    R.string.autofill_inline_subtitle_verification_code
                } else {
                    R.string.autofill_inline_subtitle_login
                }
                val inlinePresentation = createInlinePresentationOrNull(
                        it,
                        context = packageContext,
                        pendingIntent = pendingIntent,
                        title = packageContext.getString(R.string.app_name),
                        subtitle = packageContext.getString(subtitleRes),
                        iconRes = R.drawable.ic_lock_black_24dp
                )

                inlinePresentation?.let {
                    builder.setInlinePresentation(it)
                }
            }
        }

        // For now parsedAutofillRequest.parsedRequestDataSet has only the Login dataset
        for (vd in parsedAutofillRequest.parsedRequestDataSet.views) {
            val afvalue = buildAutofillValue(vd, "SOME_PLACE_HOLDER")
            builder = builder.setValue(vd.data.autofillId, afvalue)
        }
        return builder.build()
    }

    private fun buildAutofillValue(autofillView: AutofillView, value: String?): AutofillValue? {

        if (value == null) return null

        return when (autofillView.data.autofillType) {
            View.AUTOFILL_TYPE_TEXT -> AutofillValue.forText(value)
            else -> null
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("RestrictedApi")
    private fun createInlinePresentationOrNull(
            inlinePresentationSpec: InlinePresentationSpec,
            context: Context,
            pendingIntent: PendingIntent,
            title: String,
            subtitle: String,
            @DrawableRes iconRes: Int,
    ): InlinePresentation? {

        if (!UiVersions.getVersions(inlinePresentationSpec.style)
                        .contains(UiVersions.INLINE_UI_VERSION_1)) {
            return null
        }

        val icon = Icon.createWithResource(context, iconRes)

        val slice = InlineSuggestionUi
                .newContentBuilder(pendingIntent)
                // What a screen reader announces for the chip. The title and subtitle are what
                // a sighted user reads off it, and both are already localised
                .setContentDescription("$title, $subtitle")
                .setTitle(title)
                .setSubtitle(subtitle)
                .setStartIcon(icon)
                .build()
                .slice

        return InlinePresentation(
                slice,
                inlinePresentationSpec,
                false,
        )
    }
}