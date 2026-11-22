package com.mimora.app.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.mimora.app.core.model.DateRules
import com.mimora.app.core.model.DiaryEntry
import com.mimora.app.core.model.DiaryStatistics
import com.mimora.app.core.model.MediaAttachment
import com.mimora.app.core.model.MediaType
import com.mimora.app.core.model.TaskItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class MimoraDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val _dbChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val dbChanges: Flow<Unit> = _dbChanges.asSharedFlow()

    companion object {
        const val DATABASE_NAME = "mimora.db"
        const val DATABASE_VERSION = 1

        // Table Diary Entries
        const val TABLE_DIARY = "diary_entries"
        const val COL_DIARY_ID = "id"
        const val COL_DIARY_DATE = "entry_date" // YYYY-MM-DD
        const val COL_DIARY_TITLE = "title"
        const val COL_DIARY_CONTENT = "content"
        const val COL_DIARY_CREATED_AT = "created_at"
        const val COL_DIARY_UPDATED_AT = "updated_at"
        const val COL_DIARY_IS_FAVORITE = "is_favorite"

        // Table Media Attachments
        const val TABLE_MEDIA = "media_attachments"
        const val COL_MEDIA_ID = "id"
        const val COL_MEDIA_ENTRY_DATE = "entry_date"
        const val COL_MEDIA_TYPE = "media_type" // PHOTO, VOICE
        const val COL_MEDIA_FILE_PATH = "file_path"
        const val COL_MEDIA_DURATION = "duration_seconds"
        const val COL_MEDIA_CREATED_AT = "created_at"

        // Table Tasks
        const val TABLE_TASKS = "tasks"
        const val COL_TASK_ID = "id"
        const val COL_TASK_TITLE = "title"
        const val COL_TASK_DESC = "description"
        const val COL_TASK_DUE_DATE = "due_date"
        const val COL_TASK_REMINDER_TIME = "reminder_time"
        const val COL_TASK_IS_COMPLETED = "is_completed"
        const val COL_TASK_CREATED_AT = "created_at"
        const val COL_TASK_UPDATED_AT = "updated_at"
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_DIARY (
                $COL_DIARY_ID TEXT PRIMARY KEY,
                $COL_DIARY_DATE TEXT NOT NULL UNIQUE,
                $COL_DIARY_TITLE TEXT,
                $COL_DIARY_CONTENT TEXT NOT NULL,
                $COL_DIARY_CREATED_AT INTEGER NOT NULL,
                $COL_DIARY_UPDATED_AT INTEGER NOT NULL,
                $COL_DIARY_IS_FAVORITE INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

        db.execSQL("""
            CREATE INDEX idx_diary_date ON $TABLE_DIARY ($COL_DIARY_DATE)
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_MEDIA (
                $COL_MEDIA_ID TEXT PRIMARY KEY,
                $COL_MEDIA_ENTRY_DATE TEXT NOT NULL,
                $COL_MEDIA_TYPE TEXT NOT NULL,
                $COL_MEDIA_FILE_PATH TEXT NOT NULL,
                $COL_MEDIA_DURATION INTEGER NOT NULL DEFAULT 0,
                $COL_MEDIA_CREATED_AT INTEGER NOT NULL,
                FOREIGN KEY ($COL_MEDIA_ENTRY_DATE) REFERENCES $TABLE_DIARY ($COL_DIARY_DATE) ON DELETE CASCADE
            )
        """.trimIndent())

        db.execSQL("""
            CREATE INDEX idx_media_date ON $TABLE_MEDIA ($COL_MEDIA_ENTRY_DATE)
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_TASKS (
                $COL_TASK_ID TEXT PRIMARY KEY,
                $COL_TASK_TITLE TEXT NOT NULL,
                $COL_TASK_DESC TEXT,
                $COL_TASK_DUE_DATE TEXT,
                $COL_TASK_REMINDER_TIME TEXT,
                $COL_TASK_IS_COMPLETED INTEGER NOT NULL DEFAULT 0,
                $COL_TASK_CREATED_AT INTEGER NOT NULL,
                $COL_TASK_UPDATED_AT INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE INDEX idx_task_due ON $TABLE_TASKS ($COL_TASK_DUE_DATE)
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migration strategy for future database upgrades
    }

    private fun notifyChanged() {
        _dbChanges.tryEmit(Unit)
    }

    // --- DIARY OPERATIONS ---

    suspend fun saveOrUpdateEntry(entry: DiaryEntry) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val dateStr = DateRules.formatDate(entry.date)
            val cv = ContentValues().apply {
                put(COL_DIARY_ID, entry.id.ifBlank { UUID.randomUUID().toString() })
                put(COL_DIARY_DATE, dateStr)
                put(COL_DIARY_TITLE, entry.title)
                put(COL_DIARY_CONTENT, entry.content)
                put(COL_DIARY_CREATED_AT, entry.createdAt)
                put(COL_DIARY_UPDATED_AT, System.currentTimeMillis())
                put(COL_DIARY_IS_FAVORITE, if (entry.isFavorite) 1 else 0)
            }

            db.insertWithOnConflict(TABLE_DIARY, null, cv, SQLiteDatabase.CONFLICT_REPLACE)

            // Re-sync attachments for this entry date if provided
            if (entry.attachments.isNotEmpty()) {
                for (att in entry.attachments) {
                    val mcv = ContentValues().apply {
                        put(COL_MEDIA_ID, att.id.ifBlank { UUID.randomUUID().toString() })
                        put(COL_MEDIA_ENTRY_DATE, dateStr)
                        put(COL_MEDIA_TYPE, att.type.name)
                        put(COL_MEDIA_FILE_PATH, att.filePath)
                        put(COL_MEDIA_DURATION, att.durationSeconds)
                        put(COL_MEDIA_CREATED_AT, att.createdAt)
                    }
                    db.insertWithOnConflict(TABLE_MEDIA, null, mcv, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyChanged()
    }

    suspend fun getEntryByDate(date: LocalDate): DiaryEntry? = withContext(Dispatchers.IO) {
        val dateStr = DateRules.formatDate(date)
        val db = readableDatabase
        val cursor = db.query(
            TABLE_DIARY,
            null,
            "$COL_DIARY_DATE = ?",
            arrayOf(dateStr),
            null,
            null,
            null
        )

        cursor.use {
            if (it.moveToFirst()) {
                val entry = cursorToDiaryEntry(it)
                val attachments = getAttachmentsForDate(db, dateStr)
                entry.copy(attachments = attachments)
            } else null
        }
    }

    suspend fun getAllEntries(): List<DiaryEntry> = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_DIARY,
            null,
            null,
            null,
            null,
            null,
            "$COL_DIARY_DATE DESC"
        )
        val list = mutableListOf<DiaryEntry>()
        cursor.use {
            while (it.moveToNext()) {
                val entry = cursorToDiaryEntry(it)
                val dateStr = DateRules.formatDate(entry.date)
                val attachments = getAttachmentsForDate(db, dateStr)
                list.add(entry.copy(attachments = attachments))
            }
        }
        list
    }

    suspend fun getEntriesForMonth(year: Int, month: Int): List<DiaryEntry> = withContext(Dispatchers.IO) {
        val monthPrefix = String.format("%04d-%02d", year, month)
        val db = readableDatabase
        val cursor = db.query(
            TABLE_DIARY,
            null,
            "$COL_DIARY_DATE LIKE ?",
            arrayOf("$monthPrefix%"),
            null,
            null,
            "$COL_DIARY_DATE ASC"
        )
        val list = mutableListOf<DiaryEntry>()
        cursor.use {
            while (it.moveToNext()) {
                val entry = cursorToDiaryEntry(it)
                val attachments = getAttachmentsForDate(db, DateRules.formatDate(entry.date))
                list.add(entry.copy(attachments = attachments))
            }
        }
        list
    }

    suspend fun getMemoriesOnThisDay(month: Int, day: Int, excludeYear: Int): List<DiaryEntry> = withContext(Dispatchers.IO) {
        val dayPattern = String.format("%%-%02d-%02d", month, day)
        val db = readableDatabase
        val cursor = db.query(
            TABLE_DIARY,
            null,
            "$COL_DIARY_DATE LIKE ? AND $COL_DIARY_DATE NOT LIKE ?",
            arrayOf(dayPattern, String.format("%04d-%%", excludeYear)),
            null,
            null,
            "$COL_DIARY_DATE DESC"
        )
        val list = mutableListOf<DiaryEntry>()
        cursor.use {
            while (it.moveToNext()) {
                val entry = cursorToDiaryEntry(it)
                val attachments = getAttachmentsForDate(db, DateRules.formatDate(entry.date))
                list.add(entry.copy(attachments = attachments))
            }
        }
        list
    }

    suspend fun searchEntries(query: String, filterPhoto: Boolean, filterVoice: Boolean): List<DiaryEntry> = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val selectionParts = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()

        if (query.isNotBlank()) {
            selectionParts.add("($COL_DIARY_CONTENT LIKE ? OR $COL_DIARY_TITLE LIKE ?)")
            selectionArgs.add("%$query%")
            selectionArgs.add("%$query%")
        }

        val selection = if (selectionParts.isNotEmpty()) selectionParts.joinToString(" AND ") else null
        val cursor = db.query(
            TABLE_DIARY,
            null,
            selection,
            if (selectionArgs.isNotEmpty()) selectionArgs.toTypedArray() else null,
            null,
            null,
            "$COL_DIARY_DATE DESC"
        )

        val results = mutableListOf<DiaryEntry>()
        cursor.use {
            while (it.moveToNext()) {
                val entry = cursorToDiaryEntry(it)
                val attachments = getAttachmentsForDate(db, DateRules.formatDate(entry.date))
                val fullEntry = entry.copy(attachments = attachments)

                val matchesPhoto = !filterPhoto || fullEntry.hasPhoto
                val matchesVoice = !filterVoice || fullEntry.hasVoice

                if (matchesPhoto && matchesVoice) {
                    results.add(fullEntry)
                }
            }
        }
        results
    }

    suspend fun deleteEntry(date: LocalDate) = withContext(Dispatchers.IO) {
        val dateStr = DateRules.formatDate(date)
        val db = writableDatabase
        db.delete(TABLE_DIARY, "$COL_DIARY_DATE = ?", arrayOf(dateStr))
        notifyChanged()
    }

    // --- ATTACHMENTS ---

    suspend fun addAttachment(attachment: MediaAttachment) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val dateStr = DateRules.formatDate(attachment.entryDate)

        // A photo or voice note can be attached before the user has typed any
        // text. The media table has a foreign key to the diary row, so make
        // sure an (initially empty) entry exists for that day first.
        ensureEntryForDate(db, dateStr)

        val cv = ContentValues().apply {
            put(COL_MEDIA_ID, attachment.id.ifBlank { UUID.randomUUID().toString() })
            put(COL_MEDIA_ENTRY_DATE, dateStr)
            put(COL_MEDIA_TYPE, attachment.type.name)
            put(COL_MEDIA_FILE_PATH, attachment.filePath)
            put(COL_MEDIA_DURATION, attachment.durationSeconds)
            put(COL_MEDIA_CREATED_AT, attachment.createdAt)
        }
        db.insertWithOnConflict(TABLE_MEDIA, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        notifyChanged()
    }

    /** Creates an empty diary row for [dateStr] if one does not exist yet. */
    private fun ensureEntryForDate(db: SQLiteDatabase, dateStr: String) {
        val now = System.currentTimeMillis()
        val cv = ContentValues().apply {
            put(COL_DIARY_ID, UUID.randomUUID().toString())
            put(COL_DIARY_DATE, dateStr)
            put(COL_DIARY_TITLE, "")
            put(COL_DIARY_CONTENT, "")
            put(COL_DIARY_CREATED_AT, now)
            put(COL_DIARY_UPDATED_AT, now)
            put(COL_DIARY_IS_FAVORITE, 0)
        }
        db.insertWithOnConflict(TABLE_DIARY, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    suspend fun deleteAttachment(id: String) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.delete(TABLE_MEDIA, "$COL_MEDIA_ID = ?", arrayOf(id))
        notifyChanged()
    }

    private fun getAttachmentsForDate(db: SQLiteDatabase, dateStr: String): List<MediaAttachment> {
        val cursor = db.query(
            TABLE_MEDIA,
            null,
            "$COL_MEDIA_ENTRY_DATE = ?",
            arrayOf(dateStr),
            null,
            null,
            "$COL_MEDIA_CREATED_AT ASC"
        )
        val list = mutableListOf<MediaAttachment>()
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToAttachment(it))
            }
        }
        return list
    }

    // --- TASKS ---

    suspend fun saveOrUpdateTask(task: TaskItem) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_TASK_ID, task.id.ifBlank { UUID.randomUUID().toString() })
            put(COL_TASK_TITLE, task.title)
            put(COL_TASK_DESC, task.description)
            put(COL_TASK_DUE_DATE, task.dueDate?.let { DateRules.formatDate(it) })
            put(COL_TASK_REMINDER_TIME, task.reminderTime?.toString())
            put(COL_TASK_IS_COMPLETED, if (task.isCompleted) 1 else 0)
            put(COL_TASK_CREATED_AT, task.createdAt)
            put(COL_TASK_UPDATED_AT, System.currentTimeMillis())
        }
        db.insertWithOnConflict(TABLE_TASKS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        notifyChanged()
    }

    suspend fun getTasks(): List<TaskItem> = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_TASKS,
            null,
            null,
            null,
            null,
            null,
            "$COL_TASK_IS_COMPLETED ASC, $COL_TASK_DUE_DATE ASC, $COL_TASK_CREATED_AT DESC"
        )
        val list = mutableListOf<TaskItem>()
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToTask(it))
            }
        }
        list
    }

    suspend fun deleteTask(id: String) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.delete(TABLE_TASKS, "$COL_TASK_ID = ?", arrayOf(id))
        notifyChanged()
    }

    // --- STATISTICS CALCULATION ---

    suspend fun calculateStatistics(): DiaryStatistics = withContext(Dispatchers.IO) {
        val db = readableDatabase

        // Total entries
        var totalEntries = 0
        db.rawQuery("SELECT COUNT(*) FROM $TABLE_DIARY", null).use {
            if (it.moveToFirst()) totalEntries = it.getInt(0)
        }

        // Distinct writing days and streak
        val datesCursor = db.query(
            TABLE_DIARY,
            arrayOf(COL_DIARY_DATE),
            null,
            null,
            null,
            null,
            "$COL_DIARY_DATE ASC"
        )

        val uniqueDates = mutableListOf<LocalDate>()
        datesCursor.use {
            while (it.moveToNext()) {
                uniqueDates.add(DateRules.parseDate(it.getString(0)))
            }
        }

        val totalWritingDays = uniqueDates.size

        // Streak calculation
        var currentStreak = 0
        var longestStreak = 0
        var tempStreak = 0
        var prevDate: LocalDate? = null

        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        for (d in uniqueDates) {
            if (prevDate == null) {
                tempStreak = 1
            } else {
                if (d.minusDays(1) == prevDate) {
                    tempStreak++
                } else {
                    tempStreak = 1
                }
            }
            if (tempStreak > longestStreak) {
                longestStreak = tempStreak
            }
            prevDate = d
        }

        // Current streak check
        if (uniqueDates.isNotEmpty()) {
            val lastDate = uniqueDates.last()
            if (lastDate == today || lastDate == yesterday) {
                var runner = lastDate
                var c = 0
                while (uniqueDates.contains(runner)) {
                    c++
                    runner = runner.minusDays(1)
                }
                currentStreak = c
            }
        }

        // Media counts
        var photoCount = 0
        var voiceCount = 0
        db.rawQuery("SELECT $COL_MEDIA_TYPE, COUNT(*) FROM $TABLE_MEDIA GROUP BY $COL_MEDIA_TYPE", null).use {
            while (it.moveToNext()) {
                val type = it.getString(0)
                val count = it.getInt(1)
                if (type == MediaType.PHOTO.name) photoCount = count
                if (type == MediaType.VOICE.name) voiceCount = count
            }
        }

        // Tasks
        var completedTasks = 0
        var pendingTasks = 0
        db.rawQuery("SELECT $COL_TASK_IS_COMPLETED, COUNT(*) FROM $TABLE_TASKS GROUP BY $COL_TASK_IS_COMPLETED", null).use {
            while (it.moveToNext()) {
                val completed = it.getInt(0) == 1
                val count = it.getInt(1)
                if (completed) completedTasks = count else pendingTasks = count
            }
        }

        // Monthly and yearly activity
        val monthly = mutableMapOf<Int, Int>()
        val yearly = mutableMapOf<Int, Int>()

        for (d in uniqueDates) {
            monthly[d.monthValue] = (monthly[d.monthValue] ?: 0) + 1
            yearly[d.year] = (yearly[d.year] ?: 0) + 1
        }

        DiaryStatistics(
            totalEntries = totalEntries,
            totalWritingDays = totalWritingDays,
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            totalPhotos = photoCount,
            totalVoiceNotes = voiceCount,
            completedTasks = completedTasks,
            pendingTasks = pendingTasks,
            monthlyActivity = monthly,
            yearlyActivity = yearly
        )
    }

    // --- CURSOR HELPERS ---

    private fun cursorToDiaryEntry(cursor: Cursor): DiaryEntry {
        return DiaryEntry(
            id = cursor.getString(cursor.getColumnIndexOrThrow(COL_DIARY_ID)),
            date = DateRules.parseDate(cursor.getString(cursor.getColumnIndexOrThrow(COL_DIARY_DATE))),
            title = cursor.getString(cursor.getColumnIndexOrThrow(COL_DIARY_TITLE)) ?: "",
            content = cursor.getString(cursor.getColumnIndexOrThrow(COL_DIARY_CONTENT)) ?: "",
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_DIARY_CREATED_AT)),
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_DIARY_UPDATED_AT)),
            isFavorite = cursor.getInt(cursor.getColumnIndexOrThrow(COL_DIARY_IS_FAVORITE)) == 1
        )
    }

    private fun cursorToAttachment(cursor: Cursor): MediaAttachment {
        return MediaAttachment(
            id = cursor.getString(cursor.getColumnIndexOrThrow(COL_MEDIA_ID)),
            entryDate = DateRules.parseDate(cursor.getString(cursor.getColumnIndexOrThrow(COL_MEDIA_ENTRY_DATE))),
            type = MediaType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(COL_MEDIA_TYPE))),
            filePath = cursor.getString(cursor.getColumnIndexOrThrow(COL_MEDIA_FILE_PATH)),
            durationSeconds = cursor.getInt(cursor.getColumnIndexOrThrow(COL_MEDIA_DURATION)),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_MEDIA_CREATED_AT))
        )
    }

    private fun cursorToTask(cursor: Cursor): TaskItem {
        val dueStr = cursor.getString(cursor.getColumnIndexOrThrow(COL_TASK_DUE_DATE))
        val reminderStr = cursor.getString(cursor.getColumnIndexOrThrow(COL_TASK_REMINDER_TIME))
        return TaskItem(
            id = cursor.getString(cursor.getColumnIndexOrThrow(COL_TASK_ID)),
            title = cursor.getString(cursor.getColumnIndexOrThrow(COL_TASK_TITLE)),
            description = cursor.getString(cursor.getColumnIndexOrThrow(COL_TASK_DESC)) ?: "",
            dueDate = dueStr?.let { DateRules.parseDate(it) },
            reminderTime = reminderStr?.let { LocalTime.parse(it) },
            isCompleted = cursor.getInt(cursor.getColumnIndexOrThrow(COL_TASK_IS_COMPLETED)) == 1,
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TASK_CREATED_AT)),
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TASK_UPDATED_AT))
        )
    }
}
