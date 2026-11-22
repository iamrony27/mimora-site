package com.mimora.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mimora.app.feature.security.SecurityManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mimora_preferences")

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val writingThemeIndex: Int = 0,
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val pinHash: String = "",
    val fontSizeSp: Int = 16,
    val autoBackupEnabled: Boolean = false,
    val lastBackupTimestamp: Long = 0L,
    val masterRecoveryPhrase: String = "",
    val hasAcknowledgedKeyWarning: Boolean = false,
    val hasCompletedOnboarding: Boolean = false,
    val googleAccountEmail: String = "",
    val googleDriveConnected: Boolean = false,
    val writingReminderEnabled: Boolean = true,
    val firstLaunchAt: Long = 0L,
    val starPromptShown: Boolean = false
)

class UserPreferencesRepository(
    val context: Context,
    private val securityManager: SecurityManager
) {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val WRITING_THEME = intPreferencesKey("writing_theme")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val FONT_SIZE_SP = intPreferencesKey("font_size_sp")
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val LAST_BACKUP_TIMESTAMP = longPreferencesKey("last_backup_timestamp")
        val MASTER_RECOVERY_PHRASE = stringPreferencesKey("master_recovery_phrase")
        val KEY_WARNING_ACKNOWLEDGED = booleanPreferencesKey("key_warning_acknowledged")
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        val GOOGLE_ACCOUNT_EMAIL = stringPreferencesKey("google_account_email")
        val GOOGLE_DRIVE_CONNECTED = booleanPreferencesKey("google_drive_connected")
        val WRITING_REMINDER_ENABLED = booleanPreferencesKey("writing_reminder_enabled")
        val FIRST_LAUNCH_AT = longPreferencesKey("first_launch_at")
        val STAR_PROMPT_SHOWN = booleanPreferencesKey("star_prompt_shown")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { preferences ->
        val themeModeStr = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val themeMode = try {
            ThemeMode.valueOf(themeModeStr)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }

        UserPreferences(
            themeMode = themeMode,
            writingThemeIndex = preferences[PreferencesKeys.WRITING_THEME] ?: 0,
            appLockEnabled = preferences[PreferencesKeys.APP_LOCK_ENABLED] ?: false,
            biometricEnabled = preferences[PreferencesKeys.BIOMETRIC_ENABLED] ?: false,
            pinHash = preferences[PreferencesKeys.PIN_HASH] ?: "",
            fontSizeSp = preferences[PreferencesKeys.FONT_SIZE_SP] ?: 16,
            autoBackupEnabled = preferences[PreferencesKeys.AUTO_BACKUP_ENABLED] ?: false,
            lastBackupTimestamp = preferences[PreferencesKeys.LAST_BACKUP_TIMESTAMP] ?: 0L,
            masterRecoveryPhrase = preferences[PreferencesKeys.MASTER_RECOVERY_PHRASE]?.let { stored ->
                // Legacy installs wrote the phrase in plaintext (space-separated words).
                // New stores are Keystore-encrypted Base64 with no spaces.
                if (stored.contains(' ')) {
                    stored
                } else {
                    try {
                        securityManager.decryptLocalSecret(stored)
                    } catch (_: Exception) {
                        ""
                    }
                }
            } ?: "",
            hasAcknowledgedKeyWarning = preferences[PreferencesKeys.KEY_WARNING_ACKNOWLEDGED] ?: false,
            hasCompletedOnboarding = preferences[PreferencesKeys.HAS_COMPLETED_ONBOARDING] ?: false,
            googleAccountEmail = preferences[PreferencesKeys.GOOGLE_ACCOUNT_EMAIL] ?: "",
            googleDriveConnected = preferences[PreferencesKeys.GOOGLE_DRIVE_CONNECTED] ?: false,
            writingReminderEnabled = preferences[PreferencesKeys.WRITING_REMINDER_ENABLED] ?: true,
            firstLaunchAt = preferences[PreferencesKeys.FIRST_LAUNCH_AT] ?: 0L,
            starPromptShown = preferences[PreferencesKeys.STAR_PROMPT_SHOWN] ?: false
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }

    suspend fun setWritingThemeIndex(index: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WRITING_THEME] = index
        }
    }

    suspend fun setAppLock(enabled: Boolean, pinHash: String = "", biometric: Boolean = false) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_LOCK_ENABLED] = enabled
            preferences[PreferencesKeys.PIN_HASH] = pinHash
            preferences[PreferencesKeys.BIOMETRIC_ENABLED] = biometric
        }
    }

    /** Store a PIN for destructive-action confirmation without turning on full app lock. */
    suspend fun setPinHash(pinHash: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PIN_HASH] = pinHash
        }
    }

    suspend fun setFontSize(fontSizeSp: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FONT_SIZE_SP] = fontSizeSp
        }
    }

    suspend fun setAutoBackup(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_BACKUP_ENABLED] = enabled
        }
    }

    suspend fun setLastBackupTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_BACKUP_TIMESTAMP] = timestamp
        }
    }

    suspend fun setMasterRecoveryPhrase(phrase: String) {
        // Never persist the recovery phrase in plaintext. The Keystore key is
        // non-exportable, so extracting the DataStore file yields only ciphertext.
        val encrypted = securityManager.encryptLocalSecret(phrase)
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MASTER_RECOVERY_PHRASE] = encrypted
        }
    }

    suspend fun setKeyWarningAcknowledged(acknowledged: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_WARNING_ACKNOWLEDGED] = acknowledged
        }
    }

    suspend fun setCompletedOnboarding(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_COMPLETED_ONBOARDING] = completed
        }
    }

    /**
     * Only the connection state and account email are persisted.
     * Access tokens are short-lived and are never written to disk.
     */
    suspend fun setGoogleDriveConnection(email: String, connected: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.GOOGLE_ACCOUNT_EMAIL] = email
            preferences[PreferencesKeys.GOOGLE_DRIVE_CONNECTED] = connected
        }
    }

    suspend fun setWritingReminder(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WRITING_REMINDER_ENABLED] = enabled
        }
    }

    /** Anchors the one-time "star MIMORA" prompt to the very first launch. */
    suspend fun recordFirstLaunchIfNeeded() {
        context.dataStore.edit { preferences ->
            if (preferences[PreferencesKeys.FIRST_LAUNCH_AT] == null) {
                preferences[PreferencesKeys.FIRST_LAUNCH_AT] = System.currentTimeMillis()
            }
        }
    }

    suspend fun setStarPromptShown() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.STAR_PROMPT_SHOWN] = true
        }
    }

    companion object {
        /** Used by the boot receiver, which has no ViewModel scope. */
        suspend fun writingReminderEnabledOnce(context: Context): Boolean =
            context.dataStore.data.first()[PreferencesKeys.WRITING_REMINDER_ENABLED] ?: true
    }
}
