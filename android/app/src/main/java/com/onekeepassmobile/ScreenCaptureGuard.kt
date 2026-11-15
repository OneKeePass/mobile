package com.onekeepassmobile

import android.app.Activity
import android.view.WindowManager

// Holds the user's 'Block screenshots' setting for the process and applies it to an activity window.
// FLAG_SECURE blanks screenshots, screen recordings, casting and the Recents thumbnail.
// The UI sets 'blocked' each time the app preference is loaded and when the setting changes.
// Each activity that hosts the UI calls 'apply' in onResume so that an activity started later
// picks up the current value
object ScreenCaptureGuard {
    @Volatile
    var blocked: Boolean = false

    fun apply(activity: Activity) {
        if (blocked) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
