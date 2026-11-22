package com.mimora.app.feature.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

data class GoogleDriveFile(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val modifiedTime: String
)

class GoogleDriveBackupApi {

    companion object {
        private const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3/files"
        private const val FIELDS = "id,name,size,modifiedTime"

        /**
         * Builds the Drive listing URL. Kept separate so it can be unit tested:
         * `spaces=appDataFolder` is required to see the hidden app folder, and the
         * sort value must be percent-encoded (a literal `+` is rejected).
         */
        internal fun buildListUrl(): String {
            val query = "name contains 'mimora_backup' and trashed = false"
            val fields = "files(id,name,size,modifiedTime)"
            val encodedQuery = encode(query)
            val encodedFields = encode(fields)
            val encodedOrderBy = encode("modifiedTime desc")
            return "$DRIVE_API_BASE?spaces=appDataFolder" +
                "&q=$encodedQuery&fields=$encodedFields&orderBy=$encodedOrderBy"
        }

        /**
         * Create uses POST, overwrite uses PATCH with the existing file id.
         */
        internal fun buildUploadUrl(fileId: String? = null): String =
            DRIVE_UPLOAD_BASE + (fileId?.let { "/${encode(it)}" } ?: "") +
                "?uploadType=multipart&fields=$FIELDS"

        /**
         * Percent-encodes a query value using %20 for spaces. URLEncoder emits '+',
         * which is form-encoding; Drive's documented form uses %20.
         */
        internal fun encode(value: String): String =
            URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }

    private class DriveHttpException(val code: Int, message: String) : Exception(message)

    /**
     * Synchronises a single encrypted .mimora archive in the hidden appDataFolder.
     * An existing backup is overwritten in place (PATCH) so a failed upload never
     * destroys the last good copy; copies left by older versions are removed.
     */
    suspend fun uploadBackup(
        authToken: String,
        fileName: String,
        backupBytes: ByteArray
    ): Result<GoogleDriveFile> = withContext(Dispatchers.IO) {
        try {
            val existingBackups = listBackups(authToken).getOrThrow()
            val existing = existingBackups.firstOrNull()

            val uploaded = if (existing == null) {
                performUpload(authToken, null, fileName, backupBytes)
            } else {
                val updated = performUpload(authToken, existing.id, fileName, backupBytes)
                if (updated.isFailure && (updated.exceptionOrNull() as? DriveHttpException)?.code == 404) {
                    // The file vanished between list and update; recreate it.
                    performUpload(authToken, null, fileName, backupBytes)
                } else {
                    updated
                }
            }

            if (uploaded.isSuccess) {
                existingBackups.drop(1).forEach { legacy -> deleteBackup(authToken, legacy.id) }
            }
            uploaded
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun performUpload(
        authToken: String,
        fileId: String?,
        fileName: String,
        backupBytes: ByteArray
    ): Result<GoogleDriveFile> {
        val boundary = "==MIMORA_BOUNDARY_${UUID.randomUUID()}=="
        val conn = (URL(buildUploadUrl(fileId)).openConnection() as HttpURLConnection).apply {
            requestMethod = if (fileId == null) "POST" else "PATCH"
            doOutput = true
            doInput = true
            connectTimeout = 30_000
            readTimeout = 60_000
            // Stream the body instead of buffering a second copy of the archive.
            setChunkedStreamingMode(64 * 1024)
            setRequestProperty("Authorization", "Bearer $authToken")
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        }

        val metadataJson = JSONObject().apply {
            put("name", fileName)
            if (fileId == null) {
                // Only a new file needs a parent; PATCH must not move the file.
                put("parents", JSONArray().put("appDataFolder"))
                put("mimeType", "application/octet-stream")
            }
            put("description", "MIMORA Zero-Knowledge Encrypted Journal Backup (AES-256-GCM)")
        }.toString()

        conn.outputStream.buffered(64 * 1024).use { os ->
            // Part 1: Metadata
            os.write("--$boundary\r\n".toByteArray())
            os.write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
            os.write(metadataJson.toByteArray(Charsets.UTF_8))
            os.write("\r\n".toByteArray())

            // Part 2: Encrypted Payload
            os.write("--$boundary\r\n".toByteArray())
            os.write("Content-Type: application/octet-stream\r\n\r\n".toByteArray())
            os.write(backupBytes)
            os.write("\r\n".toByteArray())

            // Final boundary
            os.write("--$boundary--\r\n".toByteArray())
            os.flush()
        }

        val responseCode = conn.responseCode
        if (responseCode in 200..299) {
            val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseStr)
            val id = json.optString("id", fileId ?: "")
            val name = json.optString("name", fileName)
            val size = json.optLong("size", backupBytes.size.toLong())
            val modifiedTime = json.optString("modifiedTime", "")
            return Result.success(GoogleDriveFile(id, name, size, modifiedTime))
        }
        val errorStr = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
        return Result.failure(DriveHttpException(responseCode, "Google Drive Upload failed ($responseCode): $errorStr"))
    }

    /**
     * Lists existing MIMORA backup archives on Google Drive using Google Drive REST API v3.
     */
    suspend fun listBackups(authToken: String): Result<List<GoogleDriveFile>> = withContext(Dispatchers.IO) {
        try {
            val url = URL(buildListUrl())

            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty("Authorization", "Bearer $authToken")
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseStr)
                val filesArray = json.optJSONArray("files")
                val list = mutableListOf<GoogleDriveFile>()
                if (filesArray != null) {
                    for (i in 0 until filesArray.length()) {
                        val fo = filesArray.getJSONObject(i)
                        list.add(
                            GoogleDriveFile(
                                id = fo.getString("id"),
                                name = fo.getString("name"),
                                sizeBytes = fo.optLong("size", 0L),
                                modifiedTime = fo.optString("modifiedTime", "")
                            )
                        )
                    }
                }
                Result.success(list)
            } else {
                val errorStr = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                Result.failure(Exception("Failed to list Google Drive files ($responseCode): $errorStr"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes one backup from appDataFolder. Used to clean up duplicates created
     * before sync started overwriting a single file.
     */
    suspend fun deleteBackup(authToken: String, fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val conn = (URL("$DRIVE_API_BASE/${encode(fileId)}").openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty("Authorization", "Bearer $authToken")
            }
            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                Result.success(Unit)
            } else {
                val errorStr = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                Result.failure(DriveHttpException(responseCode, "Failed to delete old backup ($responseCode): $errorStr"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Downloads an encrypted .mimora backup archive from Google Drive using Google Drive REST API v3.
     */
    suspend fun downloadBackup(authToken: String, fileId: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$DRIVE_API_BASE/$fileId?alt=media")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 30_000
                readTimeout = 60_000
                setRequestProperty("Authorization", "Bearer $authToken")
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val bytes = conn.inputStream.use { it.readBytes() }
                Result.success(bytes)
            } else {
                val errorStr = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                Result.failure(Exception("Failed to download Google Drive file ($responseCode): $errorStr"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
