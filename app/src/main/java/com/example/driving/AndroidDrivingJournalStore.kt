package com.example.driving

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteDatabase

/** The complete accepted history and audio-intent snapshot is one SQLite transaction. This does not use
 * SharedPreferences, so failed commit's memory-visible state cannot masquerade as durable history.
 * FULL synchronous journal mode requests durable SQLite commits. No coordinates or network access. */
internal class AndroidDrivingJournalStore(context: Context) : DrivingJournalStore {
    private val helper = object : SQLiteOpenHelper(context.applicationContext, "driving_journal.db", null, 1) {
        override fun onConfigure(db: SQLiteDatabase) {
            db.execSQL("PRAGMA synchronous=FULL")
        }
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE journal_snapshot (id INTEGER PRIMARY KEY CHECK(id=1), payload BLOB NOT NULL)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            error("Unsupported driving journal migration: $oldVersion to $newVersion")
        }
    }

    @Synchronized override fun readDurable(): List<DrivingTransition> {
        helper.readableDatabase.rawQuery("SELECT payload FROM journal_snapshot WHERE id=1", null).use { cursor ->
            return if (cursor.moveToFirst()) DrivingJournalCodec.decode(cursor.getBlob(0)) else emptyList()
        }
    }

    @Synchronized override fun writeDurable(snapshot: List<DrivingTransition>): Boolean {
        return try {
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                val row = ContentValues().apply { put("id", 1); put("payload", DrivingJournalCodec.encode(snapshot)) }
                db.insertWithOnConflict("journal_snapshot", null, row, SQLiteDatabase.CONFLICT_REPLACE).also {
                    check(it != -1L) { "journal snapshot insert failed" }
                }
                db.setTransactionSuccessful()
            } finally {
                // Commit errors propagate here; a false return never authorizes an engine submission.
                db.endTransaction()
            }
            true
        } catch (_: Exception) { false }
    }

    fun close() = helper.close()
}
