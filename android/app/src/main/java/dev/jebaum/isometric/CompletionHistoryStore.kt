package dev.jebaum.isometric

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Persistence boundary for completed routines. Timestamps are UTC Unix milliseconds. */
interface CompletionHistoryStore {
    fun record(completedAtMillis: Long)
    fun latest(): Long?
    fun between(startInclusiveMillis: Long, endExclusiveMillis: Long): List<Long>
    fun close() = Unit
}

/** Keeps callers that do not need history, particularly timer tests, lightweight. */
object EmptyCompletionHistoryStore : CompletionHistoryStore {
    override fun record(completedAtMillis: Long) = Unit
    override fun latest(): Long? = null
    override fun between(startInclusiveMillis: Long, endExclusiveMillis: Long): List<Long> =
        emptyList()
}

/**
 * One row per completed routine. SQLite is already part of Android, makes each
 * completion an append rather than a rewrite of all history, and can answer a
 * calendar-month query without loading unrelated years.
 */
class SQLiteCompletionHistoryStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION),
    CompletionHistoryStore {

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE $TABLE_COMPLETIONS (" +
                "$COLUMN_COMPLETED_AT INTEGER NOT NULL PRIMARY KEY" +
                ")",
        )
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    override fun record(completedAtMillis: Long) {
        val values = ContentValues(1).apply {
            put(COLUMN_COMPLETED_AT, completedAtMillis)
        }
        writableDatabase.insertOrThrow(TABLE_COMPLETIONS, null, values)
    }

    override fun latest(): Long? = readableDatabase.query(
        TABLE_COMPLETIONS,
        arrayOf(COLUMN_COMPLETED_AT),
        null,
        null,
        null,
        null,
        "$COLUMN_COMPLETED_AT DESC",
        "1",
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.getLong(0) else null
    }

    override fun between(startInclusiveMillis: Long, endExclusiveMillis: Long): List<Long> {
        require(startInclusiveMillis <= endExclusiveMillis) { "history range is reversed" }
        return readableDatabase.query(
            TABLE_COMPLETIONS,
            arrayOf(COLUMN_COMPLETED_AT),
            "$COLUMN_COMPLETED_AT >= ? AND $COLUMN_COMPLETED_AT < ?",
            arrayOf(startInclusiveMillis.toString(), endExclusiveMillis.toString()),
            null,
            null,
            "$COLUMN_COMPLETED_AT ASC",
        ).use { cursor ->
            buildList(cursor.count) {
                while (cursor.moveToNext()) add(cursor.getLong(0))
            }
        }
    }

    override fun close() = super<SQLiteOpenHelper>.close()

    private companion object {
        const val DATABASE_NAME = "routine-history.db"
        const val DATABASE_VERSION = 1
        const val TABLE_COMPLETIONS = "completions"
        const val COLUMN_COMPLETED_AT = "completed_at"
    }
}
