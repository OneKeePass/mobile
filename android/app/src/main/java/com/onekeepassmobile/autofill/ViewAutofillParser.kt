package com.onekeepassmobile.autofill

import android.app.assist.AssistStructure
import android.os.Build
import android.service.autofill.FillRequest
import android.util.Log
import android.widget.inline.InlinePresentationSpec
import com.onekeepassmobile.autofill.util.traverse

object ViewAutofillParser {

    private const val TAG = "OkpAF"

    private var fillableLogin:FillableLogin? = FillableLogin(null,null)

    // TODO: Need to include user added restricted apps or websites
    private val IGNORE_LISTED_APP_URIS: List<String> = listOf(
            "androidapp://android",
            "androidapp://com.onekeepass",
            "androidapp://com.android.settings",
            "androidapp://com.oneplus.applocker",
    )

    // Returns ParsedAutofillRequest to build dataset
    fun startParsing(fillRequest: FillRequest):ParsedAutofillRequest? {
        return fillRequest.fillContexts.lastOrNull()?.structure?.let { assistStructure ->
            parseStructure(assistStructure,fillRequest)
        }
    }

    // Returns ParsedAutofillRequest
    private fun parseStructure(assistStructure: AssistStructure, fillRequest: FillRequest): ParsedAutofillRequest? {
        // Recursively Traverse and parse the `assistStructure` into internal models.
        val parseResultDataList = assistStructure.traverse()

        // Flatten to the autofill views for processing from the list of ParseResultData
        // Each ParseResultData has one or more autofillViews - One view for each valid view node with autofillid
        val autofillViews = parseResultDataList.map { it.autofillViews }.flatten()

        // Finds the first focused view
        val focusedView = autofillViews.firstOrNull { it.data.isFocused }

        val packageName = parseResultDataList.buildPackageNameOrNull(assistStructure)

        val uri = parseResultDataList.buildUri(packageName)

        if (focusedView == null || IGNORE_LISTED_APP_URIS.contains(uri)) {
            // The view is unfillable if there are no focused views or the URI is block listed.
            return null
        }


        val parsedRequestDataSet = when (focusedView) {
            is AutofillView.Login -> {
                val views = autofillViews.filterIsInstance<AutofillView.Login>()
                // Let us store the Login specific autofill fields for later filling with data
                storeLoginFieldInfo(views)
                ParsedRequestDataSet.Login(views = views)
            }

            is AutofillView.NotUsed -> {
                // The view is unfillable as the node is not meant to be used for autofill.
                return null
            }
        }

        val ignoreAutofillIds = parseResultDataList.map { it.ignoreAutofillIds }.flatten()

        // TODO: Need to add a flag in our app's Android Autofill page for the user to set
        //       the 'isInlineAutofillEnabled' either true or false

        // For now, we use true value so that we can enable the keyboards and other input-method editors
        // (IMEs) can display autofill suggestions inline, in a suggestion strip (also see in OkpFillResponseBuilder.buildDataset).
        // In this strip, "OneKeePass Login" will be shown and when the user presses that, the AutofillAuthenticationActivity
        // will be launched

        val maxInlineSuggestionsCount = fillRequest.getMaxInlineSuggestionsCount(isInlineAutofillEnabled = true)
        val inlinePresentationSpecs = fillRequest.getInlinePresentationSpecs(isInlineAutofillEnabled = true)

        // Parsed result that is used for autofill
        return ParsedAutofillRequest(
                inlinePresentationSpecs = inlinePresentationSpecs,
                ignoreAutofillIds = ignoreAutofillIds,
                maxInlineSuggestionsCount = maxInlineSuggestionsCount,
                packageName = packageName,
                parsedRequestDataSet = parsedRequestDataSet,
                uri = uri,)
    }

    // Remove this ?
    // Called to store the identified Login fields (username and/or password) for later auto filling the data
    private fun storeLoginFieldInfo(loginAutoviewFields: List<AutofillView.Login>) {

        for (e in loginAutoviewFields) {
            when(e) {
                is AutofillView.Login.Username -> fillableLogin?.username = e
                is AutofillView.Login.Password -> fillableLogin?.password = e
            }
        }

        Log.d(TAG, "The fillableLogin with values are $fillableLogin")
    }

    //// AssistStructure extension
    private fun AssistStructure.traverse(): List<ParseResultData> =
            (0 until windowNodeCount)
                    .map { getWindowNodeAt(it) }
                    .mapNotNull { windowNode ->
                        windowNode
                                .rootViewNode
                                ?.traverse()
                                ?.updateForMissingUsernameFields()
                    }


    //// FillRequest extensions
    private fun FillRequest?.getMaxInlineSuggestionsCount(isInlineAutofillEnabled: Boolean): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Log.d(TAG, "inlineSuggestionsRequest?.maxSuggestionCount is ${this?.inlineSuggestionsRequest?.maxSuggestionCount}")
        }

        return if (this != null && isInlineAutofillEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            inlineSuggestionsRequest?.maxSuggestionCount ?: 0
        } else {
            0
        }
    }

    private fun FillRequest?.getInlinePresentationSpecs(
            isInlineAutofillEnabled: Boolean,
    ): List<InlinePresentationSpec>?  {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Log.d(TAG,"this?.inlineSuggestionsRequest  ${this?.inlineSuggestionsRequest}")
            Log.d(TAG,"this?.inlineSuggestionsRequest?.inlinePresentationSpecs is ${this?.inlineSuggestionsRequest?.inlinePresentationSpecs}")
        }

        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // When SDK version is bellow 30, InlinePresentationSpec is not available and null
            // must be returned.
            null
        } else if (isInlineAutofillEnabled) {
            this?.inlineSuggestionsRequest?.inlinePresentationSpecs.orEmpty()
        } else {
            emptyList()
        }
    }

//    private val appScheme:String = "android"
//
//    fun buildUri(results:List<ParseResultData>, packageName: String?,): String? {
//        // Search list of ViewNodeTraversalData for a website URI.
//        results.firstOrNull { it.website != null }?.website?.let { websiteUri -> return websiteUri }
//
//        // If the package name is available, build a URI out of that.
//        return packageName?.let { nonNullPackageName -> "$appScheme://$nonNullPackageName"}
//    }

//    companion object {
//        //private val TAG = "ViewAutofillParser"
//        private val TAG = "OkpAF"
//    }

}

/*

    fun traverseStructure(structure: AssistStructure): ParseResult {
        val windowNodes: List<AssistStructure.WindowNode> =
                structure.run {
                    (0 until windowNodeCount).map {
                        getWindowNodeAt(it)
                    }
                }
        var parseResult: ParseResult = ParseResult()
        windowNodes.forEach { windowNode: AssistStructure.WindowNode ->
            val applicationId = windowNode.title.toString().split("/")[0]
            Log.d(TAG, "Autofill applicationId: $applicationId")
            parseResult.applicationId = applicationId
            val viewNode: AssistStructure.ViewNode? = windowNode.rootViewNode
            traverseNode(viewNode, parseResult)
        }

        return parseResult
    }

    private fun traverseNode(viewNode: AssistStructure.ViewNode?, parseResult: ParseResult) {
        if (viewNode?.autofillHints?.isNotEmpty() == true) {
            // If the client app provides autofill hints, you can obtain them using
            // viewNode.getAutofillHints();

            Log.d(TAG, "Hint is found for the view ${viewNode.autofillId} with autofillid of type ${viewNode.inputType}")
            val autofillId = viewNode.autofillId
            viewNode?.autofillHints?.forEach {
                when {
                    it.equals(View.AUTOFILL_HINT_USERNAME, true) -> {
                        Log.d(TAG, "Autofill hint AUTOFILL_HINT_USERNAME is found")
                        parseResult.usernameId = autofillId
                    }

                    it.equals(View.AUTOFILL_HINT_PASSWORD, true) -> {
                        Log.d(TAG, "Autofill hint AUTOFILL_HINT_PASSWORD is found")
                        parseResult.passwordId = autofillId
                    }

                    else -> {
                        //Log.d(TAG, "Autofill unsupported hint $it")
                    }
                }
            }
        } else {
            // Or use your own heuristics to describe the contents of a view
            // using methods such as getText() or getHint()
            //Log.d(TAG, "No Hint is found for the view ${viewNode?.autofillId} with autofillid of type ${viewNode?.inputType}")
        }

        val children: List<AssistStructure.ViewNode>? =
                viewNode?.run {
                    (0 until childCount).map { getChildAt(it) }
                }

        children?.forEach { childNode: AssistStructure.ViewNode ->
            traverseNode(childNode, parseResult)
        }
    }

    /**
     * Recursively traverse this [AssistStructure.ViewNode] and all of its descendants. Convert the
     * data into [ViewNodeTraversalData].
     */
    private fun AssistStructure.ViewNode.traverse(): ParseResultData {
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

 */