package com.onekeepassmobile.autofill.util


// Returns the original [String] only if it is non-null and it is not blank; Otherwise `null` is returned.
fun String?.orNullIfBlank(): String? = this?.takeUnless { it.isBlank() }