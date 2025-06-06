package com.onekeepassmobile

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log

// https://developer.android.com/guide/topics/providers/document-provider
// https://developer.android.com/reference/android/provider/DocumentsContract.html (recommended to use)
// https://developer.android.com/reference/androidx/documentfile/provider/DocumentFile (not recommended to use)
object FileUtils {
    private const val TAG = "FileUtils"
    fun getMetaInfo(contentResolver: ContentResolver, uri: Uri): FileResource? {

        val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        // May throw SecurityException "java.lang.SecurityException: Permission Denial: opening provider...."
        // if the uri is stale or non existence or invalid
        val cursor = contentResolver.query(
                uri,
                projection,
                null,
                null,
                null
        )

        val fs: FileResource? = cursor?.let {
            if (!it.moveToFirst()) {
                return null;
                //throw Exception("Uri $uri could not be found")
            }
            val displayNameColumn =
                    it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val sizeColumn =
                    it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val mimeTypeColumn =
                    it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

            // Timestamp when a document was last modified, in milliseconds since January 1, 1970 00:00:00.0 UTC
            // See https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/provider/DocumentsContract.java
            // https://developer.android.com/reference/android/provider/DocumentsContract.Document#COLUMN_LAST_MODIFIED
            
            val lastModifiedTime = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            val fs = FileResource(
                    uri = uri,
                    filename = it.getString(displayNameColumn),
                    size = it.getLong(sizeColumn),
                    mimeType = it.getString(mimeTypeColumn),
                    it.getLong(lastModifiedTime),
                    path = null,
            )
            Log.d(TAG, "File source is $fs")

            val s = uri.toString();
            val u = Uri.parse(s);
            Log.d(TAG, "Parsed uri is $u")
            return fs;
        }
        return fs;
    }
}

data class FileResource(
        val uri: Uri,
        val filename: String,
        val size: Long,
        val mimeType: String,
        val lastModifiedTime: Long,
        val path: String?,
)