package com.onekeepassmobile.autofill

import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue

class ParseResult {
    var applicationId: String? = null
    var usernameId: AutofillId? = null
    var passwordId: AutofillId? = null


    // For future Save mode
    var allowSaveValues = false

    var usernameValue: AutofillValue? = null
        set(value) {
            if (allowSaveValues)
                field = value
        }

    var passwordValue: AutofillValue? = null
        set(value) {
            if (allowSaveValues)
                field = value
        }
}