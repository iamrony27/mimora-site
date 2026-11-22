package com.mimora.app.feature.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class GoogleDriveBackupApiTest {

    private val url = GoogleDriveBackupApi.buildListUrl()

    @Test
    fun `lists only the hidden app data folder`() {
        assertTrue(
            "Drive hides appDataFolder files unless spaces=appDataFolder is requested",
            url.contains("spaces=appDataFolder")
        )
    }

    @Test
    fun `encodes the sort value instead of sending a plus sign`() {
        assertTrue("orderBy must be percent-encoded", url.contains("orderBy=modifiedTime%20desc"))
    }

    @Test
    fun `filters to mimora backups and skips trashed files`() {
        assertTrue(url.contains("name%20contains%20%27mimora_backup%27"))
        assertTrue(url.contains("trashed%20%3D%20false"))
    }

    @Test
    fun `requests only the fields the app reads`() {
        assertTrue(url.contains("fields=files%28id%2Cname%2Csize%2CmodifiedTime%29"))
    }

    @Test
    fun `points at Drive v3`() {
        assertTrue(url.startsWith("https://www.googleapis.com/drive/v3/files?"))
        assertEquals(1, url.count { it == '?' })
    }

    @Test
    fun `create upload uses the files collection`() {
        assertEquals(
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,size,modifiedTime",
            GoogleDriveBackupApi.buildUploadUrl()
        )
    }

    @Test
    fun `update upload targets the existing file with PATCH`() {
        val url = GoogleDriveBackupApi.buildUploadUrl("file-123")
        assertTrue("overwrite must target the existing file id", url.contains("/files/file-123?"))
        assertTrue("PATCH uploads still use multipart", url.endsWith("?uploadType=multipart&fields=id,name,size,modifiedTime"))
    }

    @Test
    fun `legacy duplicate names are recognised for cleanup`() {
        assertTrue(GoogleDriveBackupApi.isStableBackupName("mimora_backup_0123456789abcdef.mimora"))
        assertTrue(!GoogleDriveBackupApi.isStableBackupName("mimora_backup_2026-11-22_13-45-09.mimora"))
    }

    @Test
    fun `cloud backup name includes the exact creation time for SAF exports`() {
        assertEquals(
            "mimora_backup_2026-11-22_13-45-09.mimora",
            BackupManager.backupFileName(LocalDateTime.of(2026, 11, 22, 13, 45, 9))
        )
    }

    @Test
    fun `stable cloud name is derived only from the recovery phrase`() {
        val phrase = listOf("alpha", "beta", "gamma")
        val name = BackupManager.stableBackupFileName(phrase)
        assertTrue(name.matches(Regex("mimora_backup_[0-9a-f]{16}\\.mimora")))
        assertEquals(name, BackupManager.stableBackupFileName(phrase))
        assertEquals(name, BackupManager.stableBackupFileName(phrase.map { it.uppercase() }))
        assertTrue(name != BackupManager.stableBackupFileName(phrase + "delta"))
    }
}
