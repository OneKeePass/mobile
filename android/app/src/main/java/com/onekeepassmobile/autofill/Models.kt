package com.onekeepassmobile.autofill

import android.view.autofill.AutofillId
import android.widget.inline.InlinePresentationSpec

// Parsed View Node
sealed class AutofillView {
    data class Data(
            val autofillId: AutofillId,
            //Typically used by nodes whose View.getAutofillType() is a list to
            // indicate the meaning of each possible value in the list.
            // In the future, we may use it for credit card expiration date selection etc
            val autofillOptions: List<String>,
            val autofillType: Int,
            val isFocused: Boolean,
            val textValue: String?,)

    abstract val data: Data

    // login data partition for autofill fields
    sealed class Login : AutofillView() {
        data class Password(override val data: Data,):Login()
        data class Username(override val data: Data,) : Login()
    }

    data class NotUsed(override val data: Data,) : AutofillView()

    // TODO Need to add credit/debit card related autofill data later
}

data class FillableLogin(var username: AutofillView.Login.Username?,
                         var password: AutofillView.Login.Password?)

//sealed class ParsedAutofillRequest {
//
//    data class Fillable(
//            val ignoreAutofillIds: List<AutofillId>,
//            val inlinePresentationSpecs: List<InlinePresentationSpec>?,
//            val maxInlineSuggestionsCount: Int,
//            val packageName: String?,
//            val parsedRequestDataSet: ParsedRequestDataSet,
//            val uri: String?,
//    ) : ParsedAutofillRequest()
//
//
//    data object Unfillable : ParsedAutofillRequest()
//}

data class ParsedAutofillRequest(
        val ignoreAutofillIds: List<AutofillId>,
        val inlinePresentationSpecs: List<InlinePresentationSpec>?,
        val maxInlineSuggestionsCount: Int,
        val packageName: String?,
        val parsedRequestDataSet: ParsedRequestDataSet,
        val uri: String?,
)

sealed class ParsedRequestDataSet {

    abstract val views: List<AutofillView>

    data class Login(override val views: List<AutofillView.Login>,) : ParsedRequestDataSet()

    // credit card dataset will come here
}
