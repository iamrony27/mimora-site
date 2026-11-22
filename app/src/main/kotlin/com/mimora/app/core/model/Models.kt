package com.mimora.app.core.model

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class MediaType {
    PHOTO,
    VOICE
}

data class MediaAttachment(
    val id: String,
    val entryDate: LocalDate,
    val type: MediaType,
    val filePath: String,
    val durationSeconds: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

data class DiaryEntry(
    val id: String,
    val date: LocalDate,
    val title: String = "",
    val content: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val attachments: List<MediaAttachment> = emptyList()
) {
    val hasText: Boolean get() = content.isNotBlank()
    val hasPhoto: Boolean get() = attachments.any { it.type == MediaType.PHOTO }
    val hasVoice: Boolean get() = attachments.any { it.type == MediaType.VOICE }
    val isEmpty: Boolean get() = !hasText && attachments.isEmpty()
}

data class TaskItem(
    val id: String,
    val title: String,
    val description: String = "",
    val dueDate: LocalDate? = null,
    val reminderTime: LocalTime? = null,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class DiaryStatistics(
    val totalEntries: Int = 0,
    val totalWritingDays: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val totalPhotos: Int = 0,
    val totalVoiceNotes: Int = 0,
    val completedTasks: Int = 0,
    val pendingTasks: Int = 0,
    val monthlyActivity: Map<Int, Int> = emptyMap(), // monthIndex -> count
    val yearlyActivity: Map<Int, Int> = emptyMap()   // year -> count
)

object DateRules {
    val ISO_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * Strictly enforces that diary entries can only be created/edited for today or past dates.
     * Future dates are strictly disallowed in domain and UI layers.
     */
    fun isDateAllowedForWriting(targetDate: LocalDate, referenceDate: LocalDate = LocalDate.now()): Boolean {
        return !targetDate.isAfter(referenceDate)
    }

    fun formatDate(date: LocalDate): String = date.format(ISO_DATE_FORMATTER)

    fun parseDate(dateStr: String): LocalDate = LocalDate.parse(dateStr, ISO_DATE_FORMATTER)
}
