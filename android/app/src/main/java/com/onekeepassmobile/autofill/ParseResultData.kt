package com.onekeepassmobile.autofill

import android.app.assist.AssistStructure
import android.view.autofill.AutofillId
import com.onekeepassmobile.autofill.util.orNullIfBlank

class ParseResultData(var autofillViews: List<AutofillView>,
                      var packageId: String?,
                      var ignoreAutofillIds: List<AutofillId>,
                      var webdomain:String?,
                      var website: String?, ) {

    fun updateForMissingUsernameFields(): ParseResultData {
        val passwordPositions = this.autofillViews.mapIndexedNotNull { index, autofillView ->
            (autofillView as? AutofillView.Login.Password)?.let { index }
        }

        // Password field is found
        val updateRequired = (passwordPositions.any() && this.autofillViews.none { it is AutofillView.Login.Username })

        return if (updateRequired) {
            val updatedAutofillViews = autofillViews.mapIndexed { index, autofillView ->
                if (autofillView is AutofillView.NotUsed && passwordPositions.contains(index + 1)) {
                    AutofillView.Login.Username(data = autofillView.data)
                } else {
                    autofillView
                }
            }
            val previousUnusedIds = autofillViews
                    .filterIsInstance<AutofillView.NotUsed>()
                    .map { it.data.autofillId }
                    .toSet()
            val currentUnusedIds = updatedAutofillViews
                    .filterIsInstance<AutofillView.NotUsed>()
                    .map { it.data.autofillId }
                    .toSet()
            val unignoredAutofillIds = previousUnusedIds - currentUnusedIds

            this.autofillViews = updatedAutofillViews
            this.ignoreAutofillIds = this.ignoreAutofillIds - unignoredAutofillIds

            this

        } else {
            // We already have username fields available or there are no password fields, so no need
            // to search for them.
            this
        }
    }
}

// Gets the package name from any of the parsed result
fun List<ParseResultData>.buildPackageNameOrNull(assistStructure: AssistStructure, ): String? {
    // Search list of ViewNodeTraversalData for a valid package name.
    val traversalDataPackageName = this.firstOrNull { it.packageId != null }?.packageId

    // Try getting the package name from the AssistStructure as a last effort.
    return traversalDataPackageName ?: assistStructurePackageName(assistStructure)
}

private fun assistStructurePackageName(assistStructure: AssistStructure): String? {
    val ret  = if (assistStructure.windowNodeCount > 0) {
        assistStructure.getWindowNodeAt(0).title?.toString()?.orNullIfBlank()?.split('/')?.firstOrNull()
    } else {
        null
    }
    return ret
}

private val appScheme:String = "android"

fun List<ParseResultData>.buildUri(packageName: String?,): String? {
    // Search list of ParseResultData for a website URI.
    this.firstOrNull { it.website != null }?.website?.let { websiteUri ->  return websiteUri }

    // If the package name is available, build a URI out of that.
    return packageName?.let { nonNullPackageName -> "$appScheme://$nonNullPackageName"}
}

fun List<ParseResultData>.buildWebDomain(): String? {
    return this.firstOrNull { it.webdomain != null }?.webdomain
}

/*
data class ParseResultData(val autofillViews: List<AutofillView>,
                           val packageId: String?,
                           val ignoreAutofillIds: List<AutofillId>,
                           val webdomain:String?,
                           val website: String?, )

fun ParseResultData.updateForMissingUsernameFields(): ParseResultData {
    val passwordPositions = this.autofillViews.mapIndexedNotNull { index, autofillView ->
        (autofillView as? AutofillView.Login.Password)?.let { index }
    }

    // Password field is found
    val updateRequired = (passwordPositions.any() && this.autofillViews.none { it is AutofillView.Login.Username })

    return if (updateRequired) {
        val updatedAutofillViews = autofillViews.mapIndexed { index, autofillView ->
            if (autofillView is AutofillView.NotUsed && passwordPositions.contains(index + 1)) {
                AutofillView.Login.Username(data = autofillView.data)
            } else {
                autofillView
            }
        }
        val previousUnusedIds = autofillViews
                .filterIsInstance<AutofillView.NotUsed>()
                .map { it.data.autofillId }
                .toSet()
        val currentUnusedIds = updatedAutofillViews
                .filterIsInstance<AutofillView.NotUsed>()
                .map { it.data.autofillId }
                .toSet()
        val unignoredAutofillIds = previousUnusedIds - currentUnusedIds
        this.copy(
                autofillViews = updatedAutofillViews,
                ignoreAutofillIds = this.ignoreAutofillIds - unignoredAutofillIds,
        )
    } else {
        // We already have username fields available or there are no password fields, so no need
        // to search for them.
        this
    }
}

// Gets the package name from any of the parsed result
fun List<ParseResultData>.buildPackageNameOrNull(assistStructure: AssistStructure, ): String? {
    // Search list of ViewNodeTraversalData for a valid package name.
    val traversalDataPackageName = this.firstOrNull { it.packageId != null }?.packageId

    // Try getting the package name from the AssistStructure as a last ditch effort.
    return traversalDataPackageName ?: assistStructurePackageName(assistStructure)
}

private fun assistStructurePackageName(assistStructure: AssistStructure): String? {
    val ret  = if (assistStructure.windowNodeCount > 0) {
        assistStructure.getWindowNodeAt(0).title?.toString()?.orNullIfBlank()
                ?.split('/')?.firstOrNull()
    } else {
        null
    }
    return ret
}
*/