package com.mimora.app.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimora.app.BuildConfig
import com.mimora.app.data.preferences.ThemeMode
import com.mimora.app.data.preferences.UserPreferences
import com.mimora.app.data.preferences.UserPreferencesRepository
import com.mimora.app.feature.backup.BackupManager
import com.mimora.app.feature.backup.GoogleDriveAuth
import com.mimora.app.feature.backup.SyncOperation
import com.mimora.app.feature.reminder.WritingReminder
import com.mimora.app.feature.security.SecurityManager
import com.mimora.app.feature.updates.UpdateChecker
import com.mimora.app.feature.updates.UpdateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.mimora.app.feature.backup.GoogleDriveFile

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val recoveryPhraseWords: List<String> = emptyList(),
    val showDisclaimerModal: Boolean = false,
    val disclaimerRemainingSeconds: Int = 10,
    val canProceedDisclaimer: Boolean = false,
    val showPrivateKeyModal: Boolean = false,
    val hasCopiedPrivateKey: Boolean = false,
    val hasConfirmedSavedOffline: Boolean = false,
    val showRestoreDialog: Boolean = false,
    val pendingRestoreBytes: ByteArray? = null,
    val restorePhraseInput: String = "",
    val backupMessage: String? = null,
    val isProcessingBackup: Boolean = false,
    val syncOperation: SyncOperation = SyncOperation.IDLE,
    val pendingExportBytes: ByteArray? = null,
    val googleDriveCloudFiles: List<GoogleDriveFile> = emptyList(),
    val showGoogleDriveRestoreDialog: Boolean = false,
    val isLoadingDriveFiles: Boolean = false,
    val isCheckingUpdate: Boolean = false,
    val updateResult: UpdateResult? = null,
    val showStarPrompt: Boolean = false
)

class SettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val securityManager: SecurityManager,
    private val backupManager: BackupManager,
    private val googleDriveAuth: GoogleDriveAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    init {
        viewModelScope.launch {
            preferencesRepository.userPreferencesFlow.collectLatest { prefs ->
                val words = if (prefs.masterRecoveryPhrase.isNotBlank()) {
                    prefs.masterRecoveryPhrase.split(" ")
                } else {
                    _uiState.value.recoveryPhraseWords
                }
                _uiState.value = _uiState.value.copy(
                    preferences = prefs,
                    recoveryPhraseWords = words
                )
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun setWritingTheme(index: Int) {
        viewModelScope.launch {
            preferencesRepository.setWritingThemeIndex(index)
        }
    }

    fun setFontSize(fontSizeSp: Int) {
        viewModelScope.launch {
            preferencesRepository.setFontSize(fontSizeSp)
        }
    }

    fun toggleAppLock(enabled: Boolean, pin: String = "", biometricEnabled: Boolean? = null) {
        viewModelScope.launch {
            val current = _uiState.value.preferences
            val pinHash = when {
                !enabled -> ""
                pin.isNotBlank() -> securityManager.hashPin(pin)
                else -> current.pinHash
            }
            preferencesRepository.setAppLock(
                enabled = enabled,
                pinHash = pinHash,
                biometric = if (enabled) (biometricEnabled ?: current.biometricEnabled) else false
            )
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = _uiState.value.preferences
            if (enabled && current.pinHash.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    backupMessage = "Set a PIN before enabling fingerprint unlock."
                )
                return@launch
            }
            preferencesRepository.setAppLock(
                enabled = current.appLockEnabled,
                pinHash = current.pinHash,
                biometric = enabled
            )
        }
    }

    fun toggleAutoBackup(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAutoBackup(enabled)
        }
    }

    // --- PRIVATE KEY & 10-SECOND MANDATORY DISCLAIMER FLOW ---

    fun startDisclaimerCountdown() {
        countdownJob?.cancel()
        _uiState.value = _uiState.value.copy(
            showDisclaimerModal = true,
            disclaimerRemainingSeconds = 10,
            canProceedDisclaimer = false
        )
        countdownJob = viewModelScope.launch {
            for (i in 10 downTo 1) {
                _uiState.value = _uiState.value.copy(
                    disclaimerRemainingSeconds = i,
                    canProceedDisclaimer = false
                )
                delay(1000)
            }
            _uiState.value = _uiState.value.copy(
                disclaimerRemainingSeconds = 0,
                canProceedDisclaimer = true
            )
        }
    }

    fun dismissDisclaimer() {
        countdownJob?.cancel()
        _uiState.value = _uiState.value.copy(
            showDisclaimerModal = false,
            disclaimerRemainingSeconds = 10,
            canProceedDisclaimer = false
        )
    }

    fun acknowledgeDisclaimerAndRevealKey() {
        countdownJob?.cancel()
        viewModelScope.launch {
            preferencesRepository.setKeyWarningAcknowledged(true)
            val words = ensureRecoveryPhrase()
            _uiState.value = _uiState.value.copy(
                showDisclaimerModal = false,
                recoveryPhraseWords = words,
                showPrivateKeyModal = true,
                hasCopiedPrivateKey = false,
                hasConfirmedSavedOffline = false
            )
        }
    }

    fun copyPrivateKey(context: Context) {
        val words = _uiState.value.recoveryPhraseWords
        if (words.isNotEmpty()) {
            val appContext = context.applicationContext
            val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("MIMORA Master Private Key", words.joinToString(" "))
            clipboard.setPrimaryClip(clip)
            // Clear shortly after copying so other apps cannot read the key later.
            Handler(Looper.getMainLooper()).postDelayed({
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    clipboard.clearPrimaryClip()
                } else {
                    clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }, 60_000L)
            _uiState.value = _uiState.value.copy(hasCopiedPrivateKey = true)
        }
    }

    fun setConfirmedSavedOffline(confirmed: Boolean) {
        _uiState.value = _uiState.value.copy(hasConfirmedSavedOffline = confirmed)
    }

    fun closePrivateKeyModal() {
        _uiState.value = _uiState.value.copy(showPrivateKeyModal = false)
    }

    fun regeneratePrivateKey() {
        viewModelScope.launch {
            val newPhrase = securityManager.generate24WordRecoveryPhrase()
            preferencesRepository.setMasterRecoveryPhrase(newPhrase.joinToString(" "))
            _uiState.value = _uiState.value.copy(
                recoveryPhraseWords = newPhrase,
                hasCopiedPrivateKey = false,
                hasConfirmedSavedOffline = false
            )
        }
    }

    // --- ENCRYPTED GOOGLE DRIVE & CLOUD BACKUP / RESTORE ---

    fun prepareEncryptedBackupPayload(onReadyToSave: (ByteArray) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessingBackup = true,
                syncOperation = SyncOperation.BACKUP,
                backupMessage = "Encrypting journal with AES-256-GCM..."
            )
            try {
                if (!backupManager.hasBackupContent()) {
                    _uiState.value = _uiState.value.copy(
                        isProcessingBackup = false,
                        syncOperation = SyncOperation.IDLE,
                        pendingExportBytes = null,
                        backupMessage = "Nothing to back up yet — write an entry or add a memory first."
                    )
                    return@launch
                }
                val phraseWords = ensureRecoveryPhrase()
                val encrypted = backupManager.createEncryptedBackup(phraseWords)
                _uiState.value = _uiState.value.copy(
                    isProcessingBackup = false,
                    syncOperation = SyncOperation.IDLE,
                    pendingExportBytes = encrypted,
                    backupMessage = "Payload ready (${encrypted.size} bytes). Select Google Drive or device destination to save."
                )
                onReadyToSave(encrypted)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessingBackup = false,
                    syncOperation = SyncOperation.IDLE,
                    backupMessage = "Backup encryption failed: ${e.message}"
                )
            }
        }
    }

    fun saveBackupToUri(context: Context, uri: Uri, backupBytes: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    isProcessingBackup = true,
                    syncOperation = SyncOperation.BACKUP
                )
            }
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(backupBytes)
                    output.flush()
                }
                val now = System.currentTimeMillis()
                preferencesRepository.setLastBackupTimestamp(now)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isProcessingBackup = false,
                        syncOperation = SyncOperation.IDLE,
                        backupMessage = "Backup successfully encrypted and saved to Google Drive / Storage!",
                        pendingExportBytes = null
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isProcessingBackup = false,
                        syncOperation = SyncOperation.IDLE,
                        backupMessage = "Error saving backup file: ${e.message}"
                    )
                }
            }
        }
    }

    fun onBackupFilePickedForRestore(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                withContext(Dispatchers.Main) {
                    if (bytes != null && bytes.isNotEmpty()) {
                        // Never surface this device's key in a restore dialog.
                        // The archive may belong to a different device, so the
                        // user must type the key that protected that backup.
                        _uiState.value = _uiState.value.copy(
                            pendingRestoreBytes = bytes,
                            restorePhraseInput = "",
                            showRestoreDialog = true,
                            backupMessage = "Backup loaded (${bytes.size} bytes). Confirm your private key to decrypt."
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            backupMessage = "Selected backup file is empty."
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        backupMessage = "Failed to read backup file: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Hands a locally picked archive to the restore executor. Onboarding uses
     * its own key dialog, so it must supply the bytes without opening the
     * Settings restore dialog.
     */
    fun setPendingRestoreBytes(bytes: ByteArray) {
        _uiState.value = _uiState.value.copy(pendingRestoreBytes = bytes)
    }

    /**
     * Restores only after the caller supplies a valid 24-word key. Reports the
     * result so the onboarding flow never advances on a failed decryption.
     */
    fun executeRestore(phraseInput: String, onResult: (Result<Int>) -> Unit = {}) {
        viewModelScope.launch {
            val bytes = _uiState.value.pendingRestoreBytes
            val words = phraseInput.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
            when {
                bytes == null -> {
                    val error = IllegalStateException("No backup archive is loaded.")
                    _uiState.value = _uiState.value.copy(backupMessage = error.message)
                    onResult(Result.failure(error))
                }
                words.size != 24 -> {
                    val error = IllegalArgumentException(
                        "Recovery phrase must contain exactly 24 words (provided ${words.size})."
                    )
                    _uiState.value = _uiState.value.copy(backupMessage = error.message)
                    onResult(Result.failure(error))
                }
                else -> {
                    _uiState.value = _uiState.value.copy(
                        isProcessingBackup = true,
                        syncOperation = SyncOperation.RESTORE,
                        backupMessage = "Decrypting archive with AES-256-GCM and restoring database..."
                    )
                    try {
                        val count = backupManager.restoreFromEncryptedBackup(bytes, words)
                        // Adopt the key that actually decrypted this archive. The
                        // restored device must keep encrypting future backups with
                        // the same key or they could never be restored again.
                        preferencesRepository.setMasterRecoveryPhrase(words.joinToString(" "))
                        preferencesRepository.setKeyWarningAcknowledged(true)
                        _uiState.value = _uiState.value.copy(
                            isProcessingBackup = false,
                            syncOperation = SyncOperation.IDLE,
                            showRestoreDialog = false,
                            pendingRestoreBytes = null,
                            recoveryPhraseWords = words,
                            backupMessage = "Restore successful! Restored $count diary entries and memories."
                        )
                        onResult(Result.success(count))
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(
                            isProcessingBackup = false,
                            syncOperation = SyncOperation.IDLE,
                            backupMessage = describeRestoreFailure(e)
                        )
                        onResult(Result.failure(e))
                    }
                }
            }
        }
    }

    fun closeRestoreDialog() {
        _uiState.value = _uiState.value.copy(
            showRestoreDialog = false,
            pendingRestoreBytes = null
        )
    }

    fun clearBackupMessage() {
        _uiState.value = _uiState.value.copy(backupMessage = null)
    }

    fun setCompletedOnboarding() {
        viewModelScope.launch {
            preferencesRepository.setCompletedOnboarding(true)
        }
    }

    /**
     * Onboarding "Create New Journal": mint a unique key first, then force the
     * user through the 10-second notice + copy + confirmation save flow. The
     * journal is not created until that flow is completed, so no one can start
     * writing with a key they never saved.
     */
    fun beginFreshJournalKeySetup() {
        viewModelScope.launch {
            val words = ensureRecoveryPhrase(forceGenerate = true)
            preferencesRepository.setKeyWarningAcknowledged(false)
            _uiState.value = _uiState.value.copy(
                recoveryPhraseWords = words,
                showPrivateKeyModal = false,
                hasCopiedPrivateKey = false,
                hasConfirmedSavedOffline = false
            )
            startDisclaimerCountdown()
        }
    }

    /** Regenerate inside the onboarding key flow and restart the notice. */
    fun regenerateFreshJournalKey() {
        viewModelScope.launch {
            val words = ensureRecoveryPhrase(forceGenerate = true)
            _uiState.value = _uiState.value.copy(
                recoveryPhraseWords = words,
                showPrivateKeyModal = false,
                hasCopiedPrivateKey = false,
                hasConfirmedSavedOffline = false
            )
            startDisclaimerCountdown()
        }
    }

    /** Back out of the onboarding key flow without creating the journal. */
    fun cancelFreshJournalKeySetup() {
        countdownJob?.cancel()
        _uiState.value = _uiState.value.copy(
            showDisclaimerModal = false,
            showPrivateKeyModal = false,
            hasCopiedPrivateKey = false,
            hasConfirmedSavedOffline = false
        )
    }

    /** Reachable only after the 10s notice, Copy, and the confirmation checkbox. */
    fun finishFreshJournalSetup(onComplete: () -> Unit) {
        viewModelScope.launch {
            preferencesRepository.setKeyWarningAcknowledged(true)
            preferencesRepository.setCompletedOnboarding(true)
            _uiState.value = _uiState.value.copy(
                showDisclaimerModal = false,
                showPrivateKeyModal = false
            )
            onComplete()
        }
    }

    /** Leave the journal session. Local diary data and Drive sign-in are kept. */
    fun logout() {
        countdownJob?.cancel()
        _uiState.value = _uiState.value.copy(
            showDisclaimerModal = false,
            showPrivateKeyModal = false,
            showRestoreDialog = false,
            showGoogleDriveRestoreDialog = false,
            isProcessingBackup = false,
            syncOperation = SyncOperation.IDLE,
            backupMessage = null
        )
        viewModelScope.launch {
            preferencesRepository.logout()
        }
    }

    /** Returns this device's valid key, generating and persisting one if missing. */
    private suspend fun ensureRecoveryPhrase(forceGenerate: Boolean = false): List<String> {
        if (!forceGenerate) {
            val current = _uiState.value.recoveryPhraseWords
            if (current.size == 24 && securityManager.validateRecoveryPhrase(current)) return current
            val stored = _uiState.value.preferences.masterRecoveryPhrase
            val storedWords = stored.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
            if (storedWords.size == 24 && securityManager.validateRecoveryPhrase(storedWords)) {
                _uiState.value = _uiState.value.copy(recoveryPhraseWords = storedWords)
                return storedWords
            }
        }
        val fresh = securityManager.generate24WordRecoveryPhrase()
        preferencesRepository.setMasterRecoveryPhrase(fresh.joinToString(" "))
        _uiState.value = _uiState.value.copy(recoveryPhraseWords = fresh)
        return fresh
    }

    // --- GOOGLE DRIVE CLOUD SYNC (OAuth via AuthorizationClient) ---

    /** Asks Google for a Drive token. May return a consent intent on first use. */
    suspend fun authorizeGoogleDrive(): GoogleDriveAuth.Outcome = googleDriveAuth.authorize()

    /** Reads the token from the consent screen result. */
    fun completeGoogleDriveConsent(data: Intent?): String? =
        googleDriveAuth.tokenFromConsent(data)

    /** Called after Google returns an access token. */
    fun onGoogleDriveConnected(accessToken: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                backupMessage = "Google Drive connected. Loading backups...",
                isLoadingDriveFiles = true
            )
            val email = googleDriveAuth.fetchAccountEmail(accessToken).orEmpty()
            preferencesRepository.setGoogleDriveConnection(email, true)
            _uiState.value = _uiState.value.copy(
                preferences = _uiState.value.preferences.copy(
                    googleAccountEmail = email,
                    googleDriveConnected = true
                )
            )
            fetchGoogleDriveApiBackups(accessToken)
        }
    }

    fun onGoogleDriveAuthError(message: String) {
        _uiState.value = _uiState.value.copy(
            backupMessage = "Google sign-in failed: $message"
        )
    }

    /** Same as above, but maps Play Services status codes to readable text. */
    fun onGoogleDriveAuthFailure(error: Throwable) {
        onGoogleDriveAuthError(googleDriveAuth.describeFailure(error))
    }

    fun disconnectGoogleDrive() {
        viewModelScope.launch {
            preferencesRepository.setGoogleDriveConnection("", false)
            _uiState.value = _uiState.value.copy(
                googleDriveCloudFiles = emptyList(),
                showGoogleDriveRestoreDialog = false,
                backupMessage = "Google Drive disconnected."
            )
        }
    }

    fun uploadToGoogleDriveApi(accessToken: String) {
        viewModelScope.launch {
            if (!backupManager.hasBackupContent()) {
                _uiState.value = _uiState.value.copy(
                    backupMessage = "Nothing to sync yet — write an entry or add a memory first."
                )
                return@launch
            }
            try {
                val phraseWords = ensureRecoveryPhrase()
                val syncMarker = BackupManager.stableBackupFileName(phraseWords) +
                    "|" + backupManager.contentFingerprint()
                if (syncMarker == _uiState.value.preferences.lastBackupFingerprint) {
                    _uiState.value = _uiState.value.copy(
                        backupMessage = "Already up to date — no changes to back up."
                    )
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    isProcessingBackup = true,
                    syncOperation = SyncOperation.BACKUP,
                    backupMessage = "Encrypting and uploading to Google Drive..."
                )
                val result = backupManager.uploadToGoogleDriveApi(phraseWords, accessToken)
                if (result.isSuccess) {
                    val file = result.getOrThrow()
                    val now = System.currentTimeMillis()
                    preferencesRepository.setLastBackupTimestamp(now)
                    preferencesRepository.setLastBackupFingerprint(syncMarker)
                    _uiState.value = _uiState.value.copy(
                        isProcessingBackup = false,
                        syncOperation = SyncOperation.IDLE,
                        backupMessage = "Updated '${file.name}' (${file.sizeBytes} bytes) in Google Drive."
                    )
                    fetchGoogleDriveApiBackups(accessToken)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isProcessingBackup = false,
                        syncOperation = SyncOperation.IDLE,
                        backupMessage = "Google Drive upload failed: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessingBackup = false,
                    syncOperation = SyncOperation.IDLE,
                    backupMessage = "Google Drive upload failed: ${e.message}"
                )
            }
        }
    }

    fun openGoogleDriveRestoreDialog(accessToken: String) {
        _uiState.value = _uiState.value.copy(showGoogleDriveRestoreDialog = true)
        fetchGoogleDriveApiBackups(accessToken)
    }

    fun closeGoogleDriveRestoreDialog() {
        _uiState.value = _uiState.value.copy(showGoogleDriveRestoreDialog = false)
    }

    fun fetchGoogleDriveApiBackups(accessToken: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingDriveFiles = true)
            val result = backupManager.listGoogleDriveBackups(accessToken)
            _uiState.value = _uiState.value.copy(
                isLoadingDriveFiles = false,
                googleDriveCloudFiles = if (result.isSuccess) {
                    result.getOrThrow()
                } else {
                    _uiState.value.googleDriveCloudFiles
                },
                backupMessage = if (result.isFailure) {
                    "Could not list Google Drive backups: ${result.exceptionOrNull()?.message}"
                } else {
                    _uiState.value.backupMessage
                }
            )
        }
    }

    fun restoreFromGoogleDriveApi(
        accessToken: String,
        fileId: String,
        phraseInput: String,
        onRestored: (() -> Unit)? = null
    ) {
        val words = phraseInput.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.size != 24) {
            _uiState.value = _uiState.value.copy(
                backupMessage = "Recovery phrase must contain exactly 24 words."
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessingBackup = true,
                syncOperation = SyncOperation.RESTORE,
                backupMessage = "Downloading and restoring from Google Drive..."
            )
            val result = backupManager.downloadAndRestoreFromGoogleDriveApi(fileId, accessToken, words)
            if (result.isSuccess) {
                val count = result.getOrThrow()
                preferencesRepository.setCompletedOnboarding(true)
                preferencesRepository.setMasterRecoveryPhrase(words.joinToString(" "))
                preferencesRepository.setKeyWarningAcknowledged(true)
                _uiState.value = _uiState.value.copy(
                    isProcessingBackup = false,
                    syncOperation = SyncOperation.IDLE,
                    showGoogleDriveRestoreDialog = false,
                    backupMessage = "Restored $count diary entries from Google Drive."
                )
                onRestored?.invoke()
            } else {
                _uiState.value = _uiState.value.copy(
                    isProcessingBackup = false,
                    syncOperation = SyncOperation.IDLE,
                    backupMessage = describeRestoreFailure(
                        result.exceptionOrNull() ?: IllegalStateException("Unknown restore error")
                    )
                )
            }
        }
    }

    fun toggleWritingReminder(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setWritingReminder(enabled)
            WritingReminder.schedule(
                preferencesRepository.context.applicationContext,
                enabled
            )
        }
    }

    fun checkForUpdates() {
        if (_uiState.value.isCheckingUpdate) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCheckingUpdate = true, updateResult = null)
            _uiState.value = _uiState.value.copy(
                isCheckingUpdate = false,
                updateResult = UpdateChecker.check(BuildConfig.VERSION_NAME)
            )
        }
    }

    fun dismissUpdateResult() {
        _uiState.value = _uiState.value.copy(updateResult = null)
    }

    /**
     * The star request appears exactly once, three days after the first launch,
     * and is permanently recorded the moment it is shown.
     */
    fun maybeShowStarPrompt() {
        viewModelScope.launch {
            val prefs = preferencesRepository.userPreferencesFlow.first()
            if (prefs.starPromptShown || prefs.firstLaunchAt <= 0L) return@launch
            val threeDays = 3L * 24 * 60 * 60 * 1000
            if (System.currentTimeMillis() - prefs.firstLaunchAt < threeDays) return@launch
            preferencesRepository.setStarPromptShown()
            _uiState.value = _uiState.value.copy(showStarPrompt = true)
        }
    }

    fun dismissStarPrompt() {
        _uiState.value = _uiState.value.copy(showStarPrompt = false)
    }

    companion object {
        /** Turns crypto/IO exceptions into actionable messages without leaking key material. */
        internal fun describeRestoreFailure(error: Throwable): String {
            val message = error.message.orEmpty()
            return when {
                message.contains("BAD_DECRYPT", ignoreCase = true) ||
                    message.contains("bad decrypt", ignoreCase = true) ->
                    "Incorrect 24-word private key or a damaged backup. Nothing was restored."
                message.contains("Invalid backup format", ignoreCase = true) ->
                    "This file is not a MIMORA encrypted backup, or its format is not supported."
                message.contains("database.json missing", ignoreCase = true) ->
                    "The backup archive is incomplete (database.json missing). Nothing was restored."
                message.contains("Recovery phrase", ignoreCase = true) -> message
                else -> "Restore failed: ${message.ifBlank { "unknown error" }}. Nothing was restored."
            }
        }
    }
}
