package dev.jebaum.isometric

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** A completed routine paired with the hold weight recorded for it. */
data class WeightedCompletion(val completedAtMillis: Long, val weightLb: Double)

/** Persistence boundary for completed routines. Timestamps are UTC Unix milliseconds. */
interface CompletionHistoryStore {
    fun record(completedAtMillis: Long, weightLb: Double)
    fun latest(): Long?
    fun between(startInclusiveMillis: Long, endExclusiveMillis: Long): List<Long>

    /** Every completion with its weight, oldest first. */
    fun weightHistory(): List<WeightedCompletion>
    fun close() = Unit
}

/** Keeps callers that do not need history, particularly timer tests, lightweight. */
object EmptyCompletionHistoryStore : CompletionHistoryStore {
    override fun record(completedAtMillis: Long, weightLb: Double) = Unit
    override fun latest(): Long? = null
    override fun between(startInclusiveMillis: Long, endExclusiveMillis: Long): List<Long> =
        emptyList()
    override fun weightHistory(): List<WeightedCompletion> = emptyList()
}

/**
 * One row per completed routine. SQLite is already part of Android, makes each
 * completion an append rather than a rewrite of all history, and can answer a
 * calendar-month query without loading unrelated years.
 *
 * Queries run synchronously, including from `remember` blocks during
 * composition, and that is deliberate: they execute only when a dialog opens,
 * the month changes, or a completion lands — never per frame — and a routine
 * logged a few times a day keeps this table in the low thousands of rows for
 * years, where an indexed range query costs well under a frame. Move loading
 * to a background dispatcher only if the calendar visibly hitches on a real
 * device or history's growth outpaces this arithmetic.
 */
class SQLiteCompletionHistoryStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION),
    CompletionHistoryStore {

    override fun onCreate(database: SQLiteDatabase) {
        // Creates the v1 table and climbs the same ladder an installed device
        // does, so a column is only ever defined in one place and fresh and
        // upgraded schemas cannot drift.
        database.execSQL(
            "CREATE TABLE $TABLE_COMPLETIONS (" +
                "$COLUMN_COMPLETED_AT INTEGER NOT NULL PRIMARY KEY" +
                ")",
        )
        onUpgrade(database, 1, DATABASE_VERSION)
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        var version = oldVersion
        while (version < newVersion) {
            when (version) {
                // Completions predate weight tracking; the default of 0 records
                // them as bodyweight-only, which is what they were.
                1 -> database.execSQL(
                    "ALTER TABLE $TABLE_COMPLETIONS " +
                        "ADD COLUMN $COLUMN_WEIGHT_LB REAL NOT NULL DEFAULT 0",
                )
                else -> error("no migration from database version $version")
            }
            version++
        }
    }

    override fun record(completedAtMillis: Long, weightLb: Double) {
        val values = ContentValues(2).apply {
            put(COLUMN_COMPLETED_AT, completedAtMillis)
            put(COLUMN_WEIGHT_LB, weightLb)
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
        return queryCompletions(
            selection = "$COLUMN_COMPLETED_AT >= ? AND $COLUMN_COMPLETED_AT < ?",
            selectionArgs = arrayOf(startInclusiveMillis.toString(), endExclusiveMillis.toString()),
        ).map { it.completedAtMillis }
    }

    override fun weightHistory(): List<WeightedCompletion> =
        queryCompletions(selection = null, selectionArgs = null)

    /** The one projection over the table, so its two readers cannot drift. */
    private fun queryCompletions(
        selection: String?,
        selectionArgs: Array<String>?,
    ): List<WeightedCompletion> = readableDatabase.query(
        TABLE_COMPLETIONS,
        arrayOf(COLUMN_COMPLETED_AT, COLUMN_WEIGHT_LB),
        selection,
        selectionArgs,
        null,
        null,
        "$COLUMN_COMPLETED_AT ASC",
    ).use { cursor ->
        buildList(cursor.count) {
            while (cursor.moveToNext()) {
                add(WeightedCompletion(cursor.getLong(0), cursor.getDouble(1)))
            }
        }
    }

    override fun close() = super<SQLiteOpenHelper>.close()

    private companion object {
        const val DATABASE_NAME = "routine-history.db"

        // Installed release data must be migrated in onUpgrade, never dropped.
        const val DATABASE_VERSION = 2
        const val TABLE_COMPLETIONS = "completions"
        const val COLUMN_COMPLETED_AT = "completed_at"
        const val COLUMN_WEIGHT_LB = "weight_lb"
    }
}
