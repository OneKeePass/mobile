package com.onekeepassmobile.autofill

import android.app.assist.AssistStructure
import android.util.Log
import android.view.View

class ViewAutofillParser {

    fun traverseStructure(structure: AssistStructure): ParseResult{
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
                    it.equals(View.AUTOFILL_HINT_USERNAME,true) -> {
                        Log.d(TAG, "Autofill hint AUTOFILL_HINT_USERNAME is found")
                        parseResult.usernameId = autofillId
                    }

                    it.equals(View.AUTOFILL_HINT_PASSWORD,true) -> {
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

    companion object {
        //private val TAG = "ViewAutofillParser"
        private val TAG = "OkpAF"
    }

}