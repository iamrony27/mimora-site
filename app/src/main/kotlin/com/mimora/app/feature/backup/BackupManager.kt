package com.mimora.app.feature.backup

import android.content.Context
import com.mimora.app.core.model.DateRules
import com.mimora.app.core.model.DiaryEntry
import com.mimora.app.core.model.MediaAttachment
import com.mimora.app.core.model.MediaType
import com.mimora.app.core.model.TaskItem
import com.mimora.app.data.preferences.UserPreferencesRepository
import com.mimora.app.database.MimoraDatabaseHelper
import com.mimora.app.feature.security.SecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

sealed interface BackupProgress {
    object Idle : BackupProgress
    data class Progress(val status: String, val percent: Float) : BackupProgress
    data class Success(val message: String, val timestamp: Long) : BackupProgress
    data class Error(val error: String) : BackupProgress
}

/** Which top-bar action owns the current sync animation. */
enum class SyncOperation {
    IDLE,
    BACKUP,
    RESTORE
}

class BackupManager(
    private val context: Context,
    private val db: MimoraDatabaseHelper,
    private val securityManager: SecurityManager,
    private val preferencesRepository: UserPreferencesRepository,
    val googleDriveApi: GoogleDriveBackupApi = GoogleDriveBackupApi()
) {
    companion object {
        private val BACKUP_FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

        /** Human-readable name used for local SAF exports. */
        internal fun backupFileName(now: LocalDateTime = LocalDateTime.now()): String =
            "mimora_backup_${now.format(BACKUP_FILE_TIME)}.mimora"

        /**
         * Cloud backups use one stable file per recovery phrase: any device with
         * the same 24 words updates the same archive instead of creating a new
         * one. Only the first 8 bytes of the SHA-256 digest are used, which is
         * collision-resistant enough for a single user's handful of journals.
         */
        internal fun stableBackupFileName(recoveryPhrase: List<String>): String {
            val normalized = recoveryPhrase.joinToString(" ").trim().lowercase()
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(normalized.toByteArray(Charsets.UTF_8))
            val hex = digest.joinToString("") { "%02x".format(it) }.take(16)
            return "mimora_backup_$hex.mimora"
        }

        private fun sha256Hex(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }

    private val backupFolder = File(context.filesDir, "backups").apply { if (!exists()) mkdirs() }

    /**
     * True when there is at least one meaningful item to protect. Empty diary
     * rows are created internally when media is attached, so checking the raw
     * row count is not enough — blank rows must not produce a cloud backup.
     */
    suspend fun hasBackupContent(): Boolean = withContext(Dispatchers.IO) {
        db.getAllEntries().any { it.title.isNotBlank() || it.hasText || it.attachments.isNotEmpty() } ||
            db.getTasks().isNotEmpty()
    }

    /**
     * Stable hash of the journal's meaningful content. Volatile fields such as
     * `updatedAt` (rewritten by every local autosave) and the random encryption
     * nonce are deliberately excluded, so an unchanged diary always produces
     * the same fingerprint and no redundant cloud upload is needed.
     */
    suspend fun contentFingerprint(): String = withContext(Dispatchers.IO) {
        val entries = db.getAllEntries().filter {
            it.title.isNotBlank() || it.hasText || it.attachments.isNotEmpty()
        }
        val tasks = db.getTasks()
        val canonical = StringBuilder()
        for (e in entries.sortedBy { it.date.toEpochDay() }) {
            canonical.append("E|").append(e.id).append('|').append(e.date).append('|')
                .append(e.title).append('|').append(e.content).append('|')
                .append(e.isFavorite).append('\n')
            for (a in e.attachments.sortedBy { it.id }) {
                val file = File(a.filePath)
                val mediaState = if (file.exists()) "${file.length()}:${file.lastModified()}" else "missing"
                canonical.append("A|").append(a.id).append('|').append(a.type).append('|')
                    .append(file.name).append('|').append(a.durationSeconds).append('|')
                    .append(mediaState).append('\n')
            }
        }
        for (t in tasks.sortedBy { it.id }) {
            canonical.append("T|").append(t.id).append('|').append(t.title).append('|')
                .append(t.description).append('|').append(t.dueDate).append('|')
                .append(t.reminderTime).append('|').append(t.isCompleted).append('\n')
        }
        sha256Hex(canonical.toString())
    }

    /**
     * Builds an encrypted zip backup payload containing JSON database dump and media attachments.
     * Static assets (seasonal backgrounds, icons) are strictly excluded.
     * Blank entries are never included, and an empty payload is rejected so a
     * blank device cannot silently overwrite a real cloud backup.
     */
    suspend fun createEncryptedBackup(recoveryPhrase: List<String>): ByteArray = withContext(Dispatchers.IO) {
        val entries = db.getAllEntries().filter {
            it.title.isNotBlank() || it.hasText || it.attachments.isNotEmpty()
        }
        val tasks = db.getTasks()

        require(entries.isNotEmpty() || tasks.isNotEmpty()) {
            "There is no diary content to back up yet."
        }

        val manifest = JSONObject().apply {
            put("version", 1)
            put("app", "MIMORA")
            put("timestamp", System.currentTimeMillis())
            put("entriesCount", entries.size)
            put("tasksCount", tasks.size)
        }

        val entriesArray = JSONArray()
        for (e in entries) {
            val eo = JSONObject().apply {
                put("id", e.id)
                put("date", DateRules.formatDate(e.date))
                put("title", e.title)
                put("content", e.content)
                put("createdAt", e.createdAt)
                put("updatedAt", e.updatedAt)
                put("isFavorite", e.isFavorite)

                val attArray = JSONArray()
                for (a in e.attachments) {
                    val ao = JSONObject().apply {
                        put("id", a.id)
                        put("type", a.type.name)
                        put("fileName", File(a.filePath).name)
                        put("duration", a.durationSeconds)
                        put("createdAt", a.createdAt)
                    }
                    attArray.put(ao)
                }
                put("attachments", attArray)
            }
            entriesArray.put(eo)
        }

        val tasksArray = JSONArray()
        for (t in tasks) {
            val to = JSONObject().apply {
                put("id", t.id)
                put("title", t.title)
                put("description", t.description)
                put("dueDate", t.dueDate?.let { DateRules.formatDate(it) })
                put("reminderTime", t.reminderTime?.toString())
                put("isCompleted", t.isCompleted)
                put("createdAt", t.createdAt)
                put("updatedAt", t.updatedAt)
            }
            tasksArray.put(to)
        }

        val dbJson = JSONObject().apply {
            put("manifest", manifest)
            put("entries", entriesArray)
            put("tasks", tasksArray)
        }.toString()

        // Create Zip package
        val byteStream = ByteArrayOutputStream()
        ZipOutputStream(byteStream).use { zip ->
            // Entry 1: database.json
            val dbZipEntry = ZipEntry("database.json")
            zip.putNextEntry(dbZipEntry)
            zip.write(dbJson.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // Attachments
            val writtenMediaNames = mutableSetOf<String>()
            for (e in entries) {
                for (a in e.attachments) {
                    val file = File(a.filePath)
                    // A duplicate zip entry name would make putNextEntry throw.
                    if (file.exists() && writtenMediaNames.add(file.name)) {
                        val mediaZipEntry = ZipEntry("media/${file.name}")
                        zip.putNextEntry(mediaZipEntry)
                        FileInputStream(file).use { input ->
                            input.copyTo(zip)
                        }
                        zip.closeEntry()
                    }
                }
            }
        }

        val rawZipBytes = byteStream.toByteArray()
        // The timestamp is written only after the bytes are actually saved or
        // uploaded; preparing a payload alone must not look like a completed sync.
        securityManager.encryptWithRecoveryPhrase(rawZipBytes, recoveryPhrase)
    }

    /**
     * Decrypts and restores backup payload using the 24-word recovery phrase.
     */
    suspend fun restoreFromEncryptedBackup(
        encryptedBytes: ByteArray,
        recoveryPhrase: List<String>
    ): Int = withContext(Dispatchers.IO) {
        val decryptedZip = securityManager.decryptWithRecoveryPhrase(encryptedBytes, recoveryPhrase)

        var restoredCount = 0
        var dbJsonStr: String? = null
        val mediaFiles = mutableMapOf<String, ByteArray>()

        ZipInputStream(decryptedZip.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "database.json") {
                    dbJsonStr = String(zip.readBytes(), Charsets.UTF_8)
                } else if (entry.name.startsWith("media/")) {
                    // Never trust paths from an archive: strip directories so a
                    // crafted backup cannot write outside the app's media folders.
                    val fileName = File(entry.name.removePrefix("media/")).name
                    if (fileName.isNotBlank()) {
                        mediaFiles[fileName] = zip.readBytes()
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        if (dbJsonStr == null) {
            throw IllegalArgumentException("Corrupted backup: database.json missing.")
        }

        val rootObj = JSONObject(dbJsonStr)
        val entriesArray = rootObj.optJSONArray("entries") ?: JSONArray()
        val tasksArray = rootObj.optJSONArray("tasks") ?: JSONArray()

        val photosDir = File(context.filesDir, "photos").apply { if (!exists()) mkdirs() }
        val voiceDir = File(context.filesDir, "voice_notes").apply { if (!exists()) mkdirs() }

        // Restore entries
        for (i in 0 until entriesArray.length()) {
            val eo = entriesArray.getJSONObject(i)
            val date = DateRules.parseDate(eo.getString("date"))
            val id = eo.getString("id")
            val title = eo.optString("title", "")
            val content = eo.optString("content", "")
            val createdAt = eo.optLong("createdAt", System.currentTimeMillis())
            val updatedAt = eo.optLong("updatedAt", System.currentTimeMillis())
            val isFavorite = eo.optBoolean("isFavorite", false)

            val attArray = eo.optJSONArray("attachments") ?: JSONArray()
            val attachments = mutableListOf<MediaAttachment>()

            for (j in 0 until attArray.length()) {
                val ao = attArray.getJSONObject(j)
                val attId = ao.getString("id")
                val type = MediaType.valueOf(ao.getString("type"))
                val fileName = File(ao.getString("fileName")).name
                if (fileName.isBlank()) continue
                val duration = ao.optInt("duration", 0)
                val attCreatedAt = ao.optLong("createdAt", System.currentTimeMillis())

                val targetFolder = if (type == MediaType.PHOTO) photosDir else voiceDir
                val targetFile = File(targetFolder, fileName)

                mediaFiles[fileName]?.let { bytes ->
                    FileOutputStream(targetFile).use { it.write(bytes) }
                }

                attachments.add(
                    MediaAttachment(
                        id = attId,
                        entryDate = date,
                        type = type,
                        filePath = targetFile.absolutePath,
                        durationSeconds = duration,
                        createdAt = attCreatedAt
                    )
                )
            }

            db.saveOrUpdateEntry(
                DiaryEntry(
                    id = id,
                    date = date,
                    title = title,
                    content = content,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    isFavorite = isFavorite,
                    attachments = attachments
                )
            )
            restoredCount++
        }

        // Restore tasks
        for (i in 0 until tasksArray.length()) {
            val to = tasksArray.getJSONObject(i)
            val id = to.getString("id")
            val title = to.getString("title")
            val description = to.optString("description", "")
            val dueDate = if (to.has("dueDate") && !to.isNull("dueDate")) DateRules.parseDate(to.getString("dueDate")) else null
            val reminderTime = if (to.has("reminderTime") && !to.isNull("reminderTime")) LocalTime.parse(to.getString("reminderTime")) else null
            val isCompleted = to.optBoolean("isCompleted", false)
            val createdAt = to.optLong("createdAt", System.currentTimeMillis())
            val updatedAt = to.optLong("updatedAt", System.currentTimeMillis())

            db.saveOrUpdateTask(
                TaskItem(
                    id = id,
                    title = title,
                    description = description,
                    dueDate = dueDate,
                    reminderTime = reminderTime,
                    isCompleted = isCompleted,
                    createdAt = createdAt,
                    updatedAt = updatedAt
                )
            )
        }

        restoredCount
    }

    /**
     * Google Drive API Integration:
     * Full implementation of Google Drive REST v3 API multipart backup upload, list, and restore.
     */
    suspend fun uploadToGoogleDriveApi(
        recoveryPhrase: List<String>,
        authToken: String
    ): Result<GoogleDriveFile> {
        val backupBytes = createEncryptedBackup(recoveryPhrase)
        return googleDriveApi.uploadBackup(authToken, stableBackupFileName(recoveryPhrase), backupBytes)
    }

    suspend fun listGoogleDriveBackups(authToken: String): Result<List<GoogleDriveFile>> {
        return googleDriveApi.listBackups(authToken)
    }

    suspend fun downloadAndRestoreFromGoogleDriveApi(
        fileId: String,
        authToken: String,
        recoveryPhrase: List<String>
    ): Result<Int> {
        val downloadResult = googleDriveApi.downloadBackup(authToken, fileId)
        return if (downloadResult.isSuccess) {
            try {
                val bytes = downloadResult.getOrThrow()
                val restoredCount = restoreFromEncryptedBackup(bytes, recoveryPhrase)
                Result.success(restoredCount)
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            Result.failure(downloadResult.exceptionOrNull() ?: Exception("Google Drive download failed"))
        }
    }
}
