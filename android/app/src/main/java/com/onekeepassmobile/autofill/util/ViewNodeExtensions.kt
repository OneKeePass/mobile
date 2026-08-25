package com.onekeepassmobile.autofill.util

import android.app.assist.AssistStructure
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewStructure
import android.view.autofill.AutofillId
import android.widget.EditText
import com.onekeepassmobile.autofill.AutofillView
import com.onekeepassmobile.autofill.ParseResultData

private const val FIELD_TAG = "OkpAF"

private val ignoredOtherHints: List<String> = listOf("search", "find", "recipient", "edit", )

// Terms that mean a card security code and never a 2FA code. Checked before the otp terms
// below, otherwise a payment form's CVV field would be offered a TOTP and silently filled
// with one
private val cardSecurityCodeHints: List<String> = listOf("cvv", "cvc", "csc", "security code", "securitycode",)

// Both the run-together forms a resource id uses and the spaced forms a human readable hint
// uses, since either can be the only signal a native app gives. The card security code terms
// above are checked first, so "security code" never reaches these
private val supportedOtherTotpHints: List<String> = listOf(
        "otp", "totp", "2fa", "mfa", "authcode", "auth_code", "auth code",
        "onetime", "one_time", "one-time", "one time",
        "verificationcode", "verification_code", "verification code",
        "verifycode", "verify code", "passcode",
)

// View.AUTOFILL_HINT_SMS_OTP is API 30. The literal is used so the same detection works on
// the API 26-29 devices we still support
private const val AUTOFILL_HINT_SMS_OTP_VALUE: String = "smsOTPCode"

// The w3c autocomplete token itself. Chrome forwards the raw token as an autofill hint rather
// than mapping it onto the platform sms otp constant
private const val ONE_TIME_CODE_HINT_VALUE: String = "one-time-code"

private val supportedOtherPasswordHints: List<String> = listOf("password", "pswd")

private val supportedOtherUsernameHints: List<String> = listOf("email", "phone", "username")

private val supportedViewHints: List<String> = listOf(
        View.AUTOFILL_HINT_EMAIL_ADDRESS,
        View.AUTOFILL_HINT_PASSWORD,
        View.AUTOFILL_HINT_USERNAME,
        AUTOFILL_HINT_SMS_OTP_VALUE,
        ONE_TIME_CODE_HINT_VALUE,
)

/**
 * Attempt to convert the view node into an AutofillView. If the view node
 * doesn't contain a valid autofillId, it isn't an a view setup for autofill, so we return null.
 *
 * If it doesn't have a supported hint and isn't an input field, we also return null.
 */
fun AssistStructure.ViewNode.toAutofillView(): AutofillView? =
        // Consider only the nodes with a valid `AutofillId`
        this.autofillId?.let { nonNullAutofillId ->
                    val supportedHint = this.autofillHints?.firstOrNull { supportedViewHints.contains(it) }

                    if (supportedHint != null || this.isInputField) {
                        val autofillOptions = this.autofillOptions.orEmpty().map { it.toString() }

                        val autofillViewData = AutofillView.Data(
                                autofillId = nonNullAutofillId,
                                autofillOptions = autofillOptions,
                                autofillType = this.autofillType,
                                isFocused = this.isFocused,
                                textValue = this.autofillValue?.extractTextValue(),
                        )
                        val autofillView = buildAutofillView(
                                autofillOptions = autofillOptions,
                                autofillViewData = autofillViewData,
                                supportedHint = supportedHint,
                        )

                        // The signals each field was classified from. Field detection depends
                        // entirely on what the browser or app chooses to expose, and that varies,
                        // so a misclassified field is otherwise very hard to account for
                        Log.d(FIELD_TAG, "Field classified as ${autofillView.javaClass.simpleName}" +
                                " hints=${this.autofillHints?.joinToString()}" +
                                " idEntry=${this.idEntry} hint=${this.hint}" +
                                " htmlAttrs=${this.htmlInfo?.attributes?.joinToString { "${it.first}=${it.second}" }}")

                        autofillView
                    } else {
                        null
                    }
                }


/**
 * Recursively traverse this [AssistStructure.ViewNode] and all of its descendants. Convert the
 * data into [ViewNodeTraversalData].
 */
fun AssistStructure.ViewNode.traverse(): ParseResultData {
    // Set up mutable lists for collecting valid AutofillViews and ignorable view ids.
    val mutableAutofillViewList: MutableList<AutofillView> = mutableListOf()
    val mutableIgnoreAutofillIdList: MutableList<AutofillId> = mutableListOf()
    var packageId: String? = this.idPackage
    var website: String? = this.website
    var webdomain: String? = this.webDomain

    // Try converting this `ViewNode` into an `AutofillView`. If a valid instance is returned, add
    // it to the list. Otherwise, ignore the `AutofillId` associated with this `ViewNode`.
    toAutofillView()?.run(mutableAutofillViewList::add)
            ?: autofillId?.run(mutableIgnoreAutofillIdList::add)

    // Recursively traverse all of this view node's children.
    for (i in 0 until childCount) {
        // Extract the traversal data from each child view node and add it to the lists.
        getChildAt(i)
                .traverse()
                .let { parseResultData ->
                    parseResultData.autofillViews.forEach(mutableAutofillViewList::add)
                    parseResultData.ignoreAutofillIds.forEach(mutableIgnoreAutofillIdList::add)

                    // Get the first non-null idPackage.
                    if (packageId.isNullOrBlank() &&
                            // OS sometimes defaults node.idPackage to "android", which is not a valid
                            // package name so it is ignored to prevent auto-filling unknown applications.
                            parseResultData.packageId?.equals("android") == false
                    ) {
                        packageId = parseResultData.packageId
                    }
                    // Get the first non-null website.
                    if (website == null) {
                        website = parseResultData.website
                    }

                    if (webdomain == null) {
                        webdomain = parseResultData.webdomain
                    }
                }
    }

    // Build a new traversal data structure with this view node's data, and that of all of its
    // descendant's.
    return ParseResultData(
            autofillViews = mutableAutofillViewList,
            packageId = packageId,
            ignoreAutofillIds = mutableIgnoreAutofillIdList,
            website = website,
            webdomain = webdomain
    )
}


/**
 * The website that this [AssistStructure.ViewNode] is a part of representing.
 */
val AssistStructure.ViewNode.website: String?
    get() = this
            .webDomain
            .takeUnless { it?.isBlank() == true }
            ?.let { webDomain ->
                val webScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    this.webScheme.orNullIfBlank()?: "https"
                } else {
                    "https"
                }

                "$webScheme://$webDomain"
            }


//Attempt to convert the AssistStructure.ViewNode] and [autofillViewData] into an [AutofillView].
private fun AssistStructure.ViewNode.buildAutofillView(
        autofillOptions: List<String>,
        autofillViewData: AutofillView.Data,
        supportedHint: String?,
): AutofillView = when {

    // Checked before password/username: a 2FA code field often carries a numeric password
    // input type as well, and would otherwise be taken for a password field
    this.isTotpField(supportedHint) -> {
        AutofillView.Totp(
                data = autofillViewData,
        )
    }

    this.isPasswordField(supportedHint) -> {
        AutofillView.Login.Password(
                data = autofillViewData,
        )
    }

    this.isUsernameField(supportedHint) -> {
        AutofillView.Login.Username(
                data = autofillViewData,
        )
    }

    else -> {
        AutofillView.NotUsed(
                data = autofillViewData,
        )
    }
}

// Whether this node is a 2FA / one time code field.
//
// The explicit signals (the platform sms otp hint and the w3c autocomplete="one-time-code")
// are authoritative. The id/hint terms are a heuristic for native apps, which mostly set no
// hint at all - guarded by the card security code terms so a CVV field is never taken for a
// 2FA field
fun AssistStructure.ViewNode.isTotpField(supportedHint: String?,): Boolean {
    if (supportedHint == AUTOFILL_HINT_SMS_OTP_VALUE) return true

    if (supportedHint == ONE_TIME_CODE_HINT_VALUE) return true

    if (this.htmlInfo.isOneTimeCodeField()) return true

    // A web field carries its identity in the html name/id rather than in idEntry, which Chrome
    // leaves unset, so those are read as well before falling back to the native heuristic
    val htmlNames = this.htmlInfo.attributeValues("name", "id")

    val isCardSecurityCode = this.idEntry?.containsAnyTerms(cardSecurityCodeHints) == true ||
            this.hint?.containsAnyTerms(cardSecurityCodeHints) == true ||
            htmlNames.any { it.containsAnyTerms(cardSecurityCodeHints) }
    if (isCardSecurityCode) return false

    val isInvalidField = this.idEntry?.containsAnyTerms(ignoredOtherHints) == true ||
            this.hint?.containsAnyTerms(ignoredOtherHints) == true
    if (isInvalidField) return false

    return this.idEntry?.containsAnyTerms(supportedOtherTotpHints) == true ||
            this.hint?.containsAnyTerms(supportedOtherTotpHints) == true ||
            htmlNames.any { it.containsAnyTerms(supportedOtherTotpHints) }
}

fun AssistStructure.ViewNode.isPasswordField(supportedHint: String?,): Boolean {
    if (supportedHint == View.AUTOFILL_HINT_PASSWORD) return true

    if (this.hint?.containsAnyTerms(supportedOtherPasswordHints) == true) return true

    val isInvalidField = this.idEntry?.containsAnyTerms(ignoredOtherHints) == true ||
            this.hint?.containsAnyTerms(ignoredOtherHints) == true
    val isUsernameField = this.isUsernameField(supportedHint)
    if (this.inputType.isPasswordInput && !isInvalidField && !isUsernameField) return true

    return this.htmlInfo.isPasswordField()
}

fun AssistStructure.ViewNode.isUsernameField(supportedHint: String?,): Boolean =
        supportedHint == View.AUTOFILL_HINT_USERNAME ||
                supportedHint == View.AUTOFILL_HINT_EMAIL_ADDRESS ||
                inputType.isUsernameInput ||
                idEntry?.containsAnyTerms(supportedOtherUsernameHints) == true ||
                hint?.containsAnyTerms(supportedOtherUsernameHints) == true ||
                htmlInfo.isUsernameField()

/**
 * Whether this [AssistStructure.ViewNode] represents an input field.
 */
private val AssistStructure.ViewNode.isInputField: Boolean
    get() {
        val isEditText = className
                ?.let {
                    try {
                        Class.forName(it)
                    } catch (e: ClassNotFoundException) {
                        null
                    }
                }
                ?.let { EditText::class.java.isAssignableFrom(it) } == true
        return isEditText || htmlInfo.isInputField
    }

// String extension
private fun String.containsAnyTerms(terms: List<String>, ignoreCase: Boolean = true, ): Boolean =
        terms.any { this.contains(other = it, ignoreCase = ignoreCase,) }


// HtmlInfo Extensions

private fun ViewStructure.HtmlInfo?.isUsernameField(): Boolean =
        this?.let { htmlInfo ->
                    if (htmlInfo.isInputField) {
                        htmlInfo.attributes?.any { it.first == "type" && it.second == "email" }
                    } else {
                        false
                    }
                } ?: false

// The w3c autocomplete token browsers use for a one time code field
private fun ViewStructure.HtmlInfo?.isOneTimeCodeField(): Boolean =
        this?.let { htmlInfo ->
                    if (htmlInfo.isInputField) {
                        htmlInfo.attributes?.any {
                            it.first == "autocomplete" && it.second == "one-time-code"
                        }
                    } else {
                        false
                    }
                } ?: false

private fun ViewStructure.HtmlInfo?.isPasswordField(): Boolean =
        this?.let { htmlInfo ->  if (htmlInfo.isInputField) { htmlInfo.attributes?.any {
                                    it.first == "type" && it.second == "password"
                                }
                    } else {
                        false
                    }
                }
                ?: false

// The values of the named html attributes of an input node, in the order asked for.
private fun ViewStructure.HtmlInfo?.attributeValues(vararg names: String): List<String> =
        this?.takeIf { it.isInputField }
                ?.attributes
                ?.filter { names.contains(it.first) }
                ?.mapNotNull { it.second }
                ?: emptyList()

// Whether this HtmlInfo represents an input field.
private val ViewStructure.HtmlInfo?.isInputField: Boolean get() = this?.tag == "input"