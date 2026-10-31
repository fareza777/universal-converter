package com.universalconverter.pro.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.universalconverter.pro.core.convert.ConversionOptions
import com.universalconverter.pro.core.convert.TargetSpec
import com.universalconverter.pro.core.engine.ConversionOutcome
import org.json.JSONArray

data class HistoryEntry(
    val id: Long,
    val createdAt: Long,
    val sourceName: String,
    val sourcePath: String?,
    val sourceFormat: String,
    val targetId: String,
    val targetLabel: String,
    val outputPath: String?,
    val outputIsDirectory: Boolean,
    val outputSize: Long,
    val status: String,
    val error: String?,
    val durationMs: Long,
    val engine: String,
    val verified: Boolean,
    val verification: String,
    val checksum: String?,
    val steps: List<String>,
    val warnings: List<String>,
    val preset: String,
) {
    val succeeded: Boolean get() = status == "success"
}

class HistoryStore(context: Context) : SQLiteOpenHelper(context, "universal-converter-history.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                created_at INTEGER NOT NULL,
                source_name TEXT NOT NULL,
                source_path TEXT,
                source_format TEXT NOT NULL,
                target_id TEXT NOT NULL,
                target_label TEXT NOT NULL,
                output_path TEXT,
                output_is_dir INTEGER NOT NULL DEFAULT 0,
                output_size INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL,
                error TEXT,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                engine TEXT NOT NULL DEFAULT '',
                verified INTEGER NOT NULL DEFAULT 0,
                verification TEXT NOT NULL DEFAULT '',
                checksum TEXT,
                steps_json TEXT NOT NULL DEFAULT '[]',
                warnings_json TEXT NOT NULL DEFAULT '[]',
                preset TEXT NOT NULL DEFAULT 'BALANCED'
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX history_created ON history(created_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS history")
        onCreate(db)
    }

    @Synchronized
    fun recordSuccess(
        sourceFile: java.io.File,
        sourceName: String,
        sourceFormat: String,
        target: TargetSpec,
        options: ConversionOptions,
        outcome: ConversionOutcome,
    ): Long {
        val values = ContentValues().apply {
            put("created_at", System.currentTimeMillis())
            put("source_name", sourceName)
            put("source_path", sourceFile.absolutePath)
            put("source_format", sourceFormat)
            put("target_id", target.id)
            put("target_label", target.label)
            put("output_path", outcome.output.absolutePath)
            put("output_is_dir", if (outcome.outputIsDirectory) 1 else 0)
            put("output_size", outcome.sizeBytes)
            put("status", "success")
            put("error", null as String?)
            put("duration_ms", outcome.durationMs)
            put("engine", outcome.engine)
            put("verified", if (outcome.verified) 1 else 0)
            put("verification", outcome.verification)
            put("checksum", outcome.checksumSha256)
            put("steps_json", JSONArray(outcome.steps).toString())
            put("warnings_json", JSONArray(outcome.warnings).toString())
            put("preset", options.preset.name)
        }
        val id = writableDatabase.insert("history", null, values)
        trim()
        return id
    }

    @Synchronized
    fun recordFailure(
        sourceFile: java.io.File,
        sourceName: String,
        sourceFormat: String,
        target: TargetSpec,
        options: ConversionOptions,
        message: String,
        durationMs: Long,
    ): Long {
        val values = ContentValues().apply {
            put("created_at", System.currentTimeMillis())
            put("source_name", sourceName)
            put("source_path", sourceFile.absolutePath)
            put("source_format", sourceFormat)
            put("target_id", target.id)
            put("target_label", target.label)
            put("status", "failed")
            put("error", message.take(1000))
            put("duration_ms", durationMs)
            put("engine", "Native engine")
            put("preset", options.preset.name)
        }
        val id = writableDatabase.insert("history", null, values)
        trim()
        return id
    }

    private fun trim() {
        writableDatabase.execSQL(
            "DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY created_at DESC LIMIT 300)"
        )
    }

    @Synchronized
    fun recent(limit: Int = 200): List<HistoryEntry> {
        val out = ArrayList<HistoryEntry>()
        readableDatabase.query(
            "history", null, null, null, null, null, "created_at DESC", limit.toString(),
        ).use { cursor ->
            while (cursor.moveToNext()) out += cursor.toEntry()
        }
        return out
    }

    @Synchronized
    fun findById(id: Long): HistoryEntry? {
        readableDatabase.query("history", null, "id = ?", arrayOf(id.toString()), null, null, null, "1").use { cursor ->
            return if (cursor.moveToFirst()) cursor.toEntry() else null
        }
    }

    @Synchronized
    fun delete(id: Long) {
        writableDatabase.delete("history", "id = ?", arrayOf(id.toString()))
    }

    @Synchronized
    fun clear() {
        writableDatabase.delete("history", null, null)
    }

    private fun Cursor.toEntry(): HistoryEntry {
        fun text(column: String): String = getString(getColumnIndexOrThrow(column)) ?: ""
        fun nullable(column: String): String? {
            val index = getColumnIndexOrThrow(column)
            return if (isNull(index)) null else getString(index)
        }
        return HistoryEntry(
            id = getLong(getColumnIndexOrThrow("id")),
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
            sourceName = text("source_name"),
            sourcePath = nullable("source_path"),
            sourceFormat = text("source_format"),
            targetId = text("target_id"),
            targetLabel = text("target_label"),
            outputPath = nullable("output_path"),
            outputIsDirectory = getInt(getColumnIndexOrThrow("output_is_dir")) == 1,
            outputSize = getLong(getColumnIndexOrThrow("output_size")),
            status = text("status"),
            error = nullable("error"),
            durationMs = getLong(getColumnIndexOrThrow("duration_ms")),
            engine = text("engine"),
            verified = getInt(getColumnIndexOrThrow("verified")) == 1,
            verification = text("verification"),
            checksum = nullable("checksum"),
            steps = parseList(text("steps_json")),
            warnings = parseList(text("warnings_json")),
            preset = text("preset"),
        )
    }

    private fun parseList(json: String): List<String> {
        return try {
            val array = JSONArray(json)
            List(array.length()) { array.optString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}