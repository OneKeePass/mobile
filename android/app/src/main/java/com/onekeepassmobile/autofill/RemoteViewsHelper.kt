package com.onekeepassmobile.autofill

import android.widget.RemoteViews
import com.onekeepassmobile.R

// Need to use 'import com.onekeepassmobile.R'
// See https://stackoverflow.com/questions/16045118/cannot-find-r-layout-activity-main

object RemoteViewsHelper {

    fun overlayPresentation(packageName:String):RemoteViews {
        val remoteViews = RemoteViews(packageName,R.layout.autofill_remoteview)
        remoteViews.apply {
            setTextViewText(R.id.text,"OneKeePass")
            //setImageViewResource()
        }
        return remoteViews
    }
}