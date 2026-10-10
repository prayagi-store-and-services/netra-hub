package com.example.driving

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/** Read-only. Returns one row: driving (1 or 0) and updated_at. Nothing else, no personal data, no writes. */
class DrivingSignalProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val c = context!!
        val cur = MatrixCursor(arrayOf("driving", "updated_at"))
        cur.addRow(arrayOf<Any>(if (DrivingSignal.isDriving(c)) 1 else 0, DrivingSignal.since(c)))
        return cur
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.netra.driving"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
