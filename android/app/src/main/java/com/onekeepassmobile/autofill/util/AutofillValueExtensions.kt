package com.onekeepassmobile.autofill.util

import android.view.autofill.AutofillValue

fun AutofillValue.extractTextValue(): String? =
        if (this.isText) {
            this.textValue.takeIf { it.isNotBlank()}?.toString()
        } else {
            null
        }