package com.onekeepassmobile

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log

// https://developer.android.com/guide/topics/providers/document-provider
// https://developer.android.com/reference/android/provider/DocumentsContract.html (recommended to use)
// https://developer.android.com/reference/androidx/documentfile/provider/DocumentFile (not recommended to use)
object FileUtils {
    private const val TAG = "FileUtils"

    /**
     * Reads whatever the provider behind the uri is willing to tell us about it
     *
     * A projection is deliberately not passed and every column is read only if the cursor
     * actually carries it. Not every app that offers 'Open with' or 'Open in' hands out a
     * DocumentsProvider uri - OneDrive gives a 'content://com.microsoft.skydrive.provider/streamcache/...'
     * one whose cursor carries only the OpenableColumns. Asking such a provider for the
     * DocumentsContract columns used to fail the whole call and left us with no file name,
     * which in turn made the backup file creation fail after the db itself had been read
     */
    fun getMetaInfo(contentResolver: ContentResolver, uri: Uri): FileResource? {
        // May throw SecurityException "java.lang.SecurityException: Permission Denial: opening provider...."
        // if the uri is stale or non existence or invalid
        val cursor = contentResolver.query(uri, null, null, null, null) ?: return null

        return cursor.use {
            if (!it.moveToFirst()) {
                null
            } else {
                val fs = FileResource(
                        uri = uri,
                        filename = it.stringOrNull(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                                ?: fileNameFromUri(uri),
                        size = it.longOrNull(DocumentsContract.Document.COLUMN_SIZE),
                        mimeType = it.stringOrNull(DocumentsContract.Document.COLUMN_MIME_TYPE),
                        // Timestamp when a document was last modified, in milliseconds since January 1, 1970 00:00:00.0 UTC
                        // See https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/provider/DocumentsContract.java
                        // https://developer.android.com/reference/android/provider/DocumentsContract.Document#COLUMN_LAST_MODIFIED
                        lastModifiedTime = it.longOrNull(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                        path = null,
                )
                Log.d(TAG, "File source is $fs")
                fs
            }
        }
    }

    /**
     * The last path segment of the uri as the file name. This is all we have when the provider
     * answers with no display name and an empty name would fail the backup file naming
     */
    fun fileNameFromUri(uri: Uri): String? {
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    }

    private fun Cursor.stringOrNull(columnName: String): String? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    private fun Cursor.longOrNull(columnName: String): Long? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getLong(index) else null
    }
}

data class FileResource(
        val uri: Uri,
        // All of these are null when the provider does not supply the matching column
        val filename: String?,
        val size: Long?,
        val mimeType: String?,
        val lastModifiedTime: Long?,
        val path: String?,
)
