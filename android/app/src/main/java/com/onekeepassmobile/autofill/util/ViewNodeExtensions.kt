package com.onekeepassmobile.autofill.util

import android.app.assist.AssistStructure
import android.os.Build
import android.view.View
import android.view.ViewStructure
import android.view.autofill.AutofillId
import android.widget.EditText
import com.onekeepassmobile.autofill.AutofillView
import com.onekeepassmobile.autofill.ParseResultData

private val ignoredOtherHints: List<String> = listOf("search", "find", "recipient", "edit", )

private val supportedOtherPasswordHints: List<String> = listOf("password", "pswd")

private val supportedOtherUsernameHints: List<String> = listOf("email", "phone", "username")

private val supportedViewHints: List<String> = listOf(
        View.AUTOFILL_HINT_EMAIL_ADDRESS,
        View.AUTOFILL_HINT_PASSWORD,
        View.AUTOFILL_HINT_USERNAME,
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
                        buildAutofillView(
                                autofillOptions = autofillOptions,
                                autofillViewData = autofillViewData,
                                supportedHint = supportedHint,
                        )
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

private fun ViewStructure.HtmlInfo?.isPasswordField(): Boolean =
        this?.let { htmlInfo ->  if (htmlInfo.isInputField) { htmlInfo.attributes?.any {
                                    it.first == "type" && it.second == "password"
                                }
                    } else {
                        false
                    }
                }
                ?: false

// Whether this HtmlInfo represents an input field.
private val ViewStructure.HtmlInfo?.isInputField: Boolean get() = this?.tag == "input"