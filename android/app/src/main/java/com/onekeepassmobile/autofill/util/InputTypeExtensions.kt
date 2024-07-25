package com.onekeepassmobile.autofill.util

import android.app.PendingIntent
import android.os.Build
import android.text.InputType

val Int.isPasswordInput: Boolean
    get() {

        val isMultiline = this.checkFlag(InputType.TYPE_TEXT_VARIATION_PASSWORD) &&
                this.checkFlag(InputType.TYPE_TEXT_FLAG_MULTI_LINE)

        val isPasswordInputType = this.checkFlag(InputType.TYPE_TEXT_VARIATION_PASSWORD) ||
                this.checkFlag(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) ||
                this.checkFlag(InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)

        return !isMultiline && isPasswordInputType
    }

val Int.isUsernameInput: Boolean
    get() = this.checkFlag(InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)


private fun Int.checkFlag(flag: Int): Boolean = (this and flag) == flag