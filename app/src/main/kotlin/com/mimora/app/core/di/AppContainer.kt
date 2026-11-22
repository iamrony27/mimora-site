package com.mimora.app.core.di

import android.content.Context
import com.mimora.app.data.preferences.UserPreferencesRepository
import com.mimora.app.data.repository.DiaryRepository
import com.mimora.app.data.repository.MediaRepository
import com.mimora.app.data.repository.StatisticsRepository
import com.mimora.app.data.repository.TaskRepository
import com.mimora.app.database.MimoraDatabaseHelper
import com.mimora.app.feature.backup.BackupManager
import com.mimora.app.feature.backup.GoogleDriveAuth
import com.mimora.app.feature.security.SecurityManager

class AppContainer(val context: Context) {
    val databaseHelper: MimoraDatabaseHelper by lazy {
        MimoraDatabaseHelper(context)
    }

    val preferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(context, securityManager)
    }

    val diaryRepository: DiaryRepository by lazy {
        DiaryRepository(databaseHelper)
    }

    val mediaRepository: MediaRepository by lazy {
        MediaRepository(context, databaseHelper)
    }

    val taskRepository: TaskRepository by lazy {
        TaskRepository(context, databaseHelper)
    }

    val statisticsRepository: StatisticsRepository by lazy {
        StatisticsRepository(databaseHelper)
    }

    val securityManager: SecurityManager by lazy {
        SecurityManager(context)
    }

    val backupManager: BackupManager by lazy {
        BackupManager(context, databaseHelper, securityManager, preferencesRepository)
    }

    val googleDriveAuth: GoogleDriveAuth by lazy {
        GoogleDriveAuth(context.applicationContext)
    }
}
