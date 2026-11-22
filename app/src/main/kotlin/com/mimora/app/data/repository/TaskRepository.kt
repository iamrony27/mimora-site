package com.mimora.app.data.repository

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mimora.app.core.model.TaskItem
import com.mimora.app.database.MimoraDatabaseHelper
import com.mimora.app.feature.tasks.TaskReminderWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class TaskRepository(
    private val context: Context,
    private val db: MimoraDatabaseHelper
) {
    fun getTasks(): Flow<List<TaskItem>> = flow {
        emit(db.getTasks())
        db.dbChanges.collect {
            emit(db.getTasks())
        }
    }

    suspend fun saveTask(task: TaskItem) {
        db.saveOrUpdateTask(task)
        scheduleReminderIfNeeded(task)
    }

    suspend fun toggleTask(task: TaskItem) {
        val updated = task.copy(isCompleted = !task.isCompleted)
        db.saveOrUpdateTask(updated)
    }

    suspend fun deleteTask(id: String) {
        db.deleteTask(id)
        WorkManager.getInstance(context).cancelAllWorkByTag("task_$id")
    }

    private fun scheduleReminderIfNeeded(task: TaskItem) {
        val dueDate = task.dueDate ?: return
        val reminderTime = task.reminderTime ?: return
        if (task.isCompleted) return

        val triggerDateTime = LocalDateTime.of(dueDate, reminderTime)
        val now = LocalDateTime.now()

        if (triggerDateTime.isAfter(now)) {
            val delayMillis = triggerDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() -
                    System.currentTimeMillis()

            val workData = Data.Builder()
                .putString(TaskReminderWorker.KEY_TASK_TITLE, task.title)
                .putString(TaskReminderWorker.KEY_TASK_DESC, task.description)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<TaskReminderWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(workData)
                .addTag("task_${task.id}")
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
