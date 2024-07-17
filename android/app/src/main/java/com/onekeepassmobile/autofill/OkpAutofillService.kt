package com.onekeepassmobile.autofill

import android.R
import android.app.assist.AssistStructure
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillContext
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.util.Log
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews

//class OkpAutofillService : AutofillService() {
//
//    override fun onFillRequest(request: FillRequest,
//                               cancellationSignal: CancellationSignal,
//                               callback: FillCallback) {
//        // Get the structure from the request
//        val context: List<FillContext> = request.fillContexts
//        val structure: AssistStructure = context[context.size - 1].structure
//
//        // Gets the package name from the calling activity
//        val packageName: String = structure.activityComponent.packageName
//        Log.d(TAG,"Package name of activity triggered autofill ${packageName}")
//
//        // Traverse the structure looking for nodes to fill out
//
//        val parsedStructure: ParseResult = ViewAutofillParser().traverseStructure(structure)//parseStructure(structure)
//
//        // Fetch user data that matches the fields
//        val (username: String, password: String) = UserData("TestUser", "TestPass123") //fetchUserData(parsedStructure)
//
//        // Build the presentation of the datasets
//        val usernamePresentation = RemoteViews(packageName, R.layout.simple_list_item_1)
//        usernamePresentation.setTextViewText(android.R.id.text1, "my_username")
//        val passwordPresentation = RemoteViews(packageName, android.R.layout.simple_list_item_1)
//        passwordPresentation.setTextViewText(android.R.id.text1, "Password for my_username")
//
//
//        var b = Dataset.Builder()
//
//        if( parsedStructure.usernameId != null ) {
//            b = b.setValue(
//                    parsedStructure.usernameId!!,
//                    AutofillValue.forText(username),
//                    usernamePresentation
//            )
//        }
//
//        if( parsedStructure.passwordId != null ) {
//            b = b.setValue(
//                    parsedStructure.passwordId!!,
//                    AutofillValue.forText(password),
//                    passwordPresentation
//            )
//        }
//
//        if ( parsedStructure.usernameId == null && parsedStructure.passwordId == null)  {
//            callback.onFailure("No user and password hints are found")
//            return
//        }
//
//        val fillResponse: FillResponse = FillResponse.Builder().addDataset(b.build()).build();
//        // If there are no errors, call onSuccess() and pass the response
//        callback.onSuccess(fillResponse)
//
//
//        // Add a dataset to the response
////        val fillResponse: FillResponse = FillResponse.Builder()
////                .addDataset(Dataset.Builder()
////                        .setValue(
////                                parsedStructure.usernameId,
////                                AutofillValue.forText(username),
////                                usernamePresentation
////                        )
////                        .setValue(
////                                parsedStructure.passwordId,
////                                AutofillValue.forText(password),
////                                passwordPresentation
////                        )
////                        .build())
////                .build()
//
////        // If there are no errors, call onSuccess() and pass the response
////        callback.onSuccess(fillResponse)
//
//    }
//
////    private fun fetchUserData(parsedStructure: ParsedStructure): UserData {
////        TODO("Not yet implemented")
////    }
////
////    private fun parseStructure(structure: AssistStructure): ParsedStructure {
////        TODO("Not yet implemented")
////    }
//
//    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
//        TODO("Not yet implemented")
//    }
//
//    companion object {
//        private val TAG = "OkpAutofillService"
//    }
//}
//
//data class ParsedStructure(var usernameId: AutofillId, var passwordId: AutofillId)
//
//data class UserData(var username: String, var password: String)
