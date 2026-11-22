package com.mimora.app.feature.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimora.app.core.model.DiaryEntry
import com.mimora.app.core.model.MediaAttachment
import com.mimora.app.data.repository.DiaryRepository
import com.mimora.app.data.repository.MediaRepository
import com.mimora.app.data.repository.PlaybackState
import com.mimora.app.data.repository.RecordingState
import com.mimora.app.design.DailyMood
import com.mimora.app.design.SaveState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

data class HomeUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val title: String = "",
    val content: String = "",
    val attachments: List<MediaAttachment> = emptyList(),
    val saveState: SaveState = SaveState.IDLE,
    val canGoForward: Boolean = false,
    val isToday: Boolean = true,
    val moodId: String = DailyMood.CALM.id
)

class HomeViewModel(
    private val diaryRepository: DiaryRepository,
    private val mediaRepository: MediaRepository,
    /** Notified only when the user explicitly taps Save, so cloud backup is user-controlled. */
    private val onSaveRequested: () -> Unit = {}
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val recordingState: StateFlow<RecordingState> = mediaRepository.recordingState
    val playbackState: StateFlow<PlaybackState> = mediaRepository.playbackState

    private var currentEntry: DiaryEntry? = null
    private var autosaveJob: Job? = null
    private var entryLoadJob: Job? = null

    /** True while the editor holds text that has not reached the database yet. */
    private var hasUnsavedEdits = false

    init {
        loadEntryForDate(LocalDate.now())
    }

    fun selectDate(date: LocalDate) {
        // Hard enforce date restriction: cannot select future dates
        if (date.isAfter(LocalDate.now())) return
        switchDate(date)
    }

    fun goToPreviousDay() {
        switchDate(_uiState.value.selectedDate.minusDays(1))
    }

    fun goToNextDay() {
        val current = _uiState.value.selectedDate
        if (current.isBefore(LocalDate.now())) {
            switchDate(current.plusDays(1))
        }
    }

    fun goToToday() {
        switchDate(LocalDate.now())
    }

    /** Never abandon a pending debounced save when the user changes date. */
    private fun switchDate(date: LocalDate) {
        viewModelScope.launch {
            flushPendingSave()
            loadEntryForDate(date)
        }
    }

    fun onMoodSelected(mood: DailyMood) {
        _uiState.value = _uiState.value.copy(moodId = mood.id)
    }

    private fun loadEntryForDate(date: LocalDate) {
        // Enforce future date check
        val targetDate = if (date.isAfter(LocalDate.now())) LocalDate.now() else date
        entryLoadJob?.cancel()

        entryLoadJob = viewModelScope.launch {
            diaryRepository.getEntry(targetDate).collectLatest { entry ->
                currentEntry = entry
                val current = _uiState.value
                // Database writes re-emit the entry flow (media, tasks, saves).
                // Keep the live draft while it has unsaved edits so typing never
                // jumps backwards underneath the cursor.
                val preserveDraft = hasUnsavedEdits && current.selectedDate == targetDate
                _uiState.value = current.copy(
                    selectedDate = targetDate,
                    title = if (preserveDraft) current.title else (entry?.title ?: ""),
                    content = if (preserveDraft) current.content else (entry?.content ?: ""),
                    attachments = if (preserveDraft && entry == null) {
                        current.attachments
                    } else {
                        entry?.attachments ?: emptyList()
                    },
                    saveState = when {
                        hasUnsavedEdits -> SaveState.SAVING
                        entry != null && isMeaningful(entry) -> SaveState.SAVED
                        else -> SaveState.IDLE
                    },
                    canGoForward = targetDate.isBefore(LocalDate.now()),
                    isToday = targetDate == LocalDate.now()
                )
            }
        }
    }

    fun onTitleChanged(newTitle: String) {
        hasUnsavedEdits = true
        _uiState.value = _uiState.value.copy(title = newTitle)
        triggerAutosave()
    }

    fun onContentChanged(newContent: String) {
        hasUnsavedEdits = true
        _uiState.value = _uiState.value.copy(content = newContent)
        triggerAutosave()
    }

    private fun buildEntry(state: HomeUiState, existing: DiaryEntry?) = DiaryEntry(
        id = existing?.id ?: "",
        date = state.selectedDate,
        title = state.title,
        content = state.content,
        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        isFavorite = existing?.isFavorite ?: false,
        attachments = state.attachments
    )

    /** A title-only reflection is a real entry, not blank data. */
    private fun isMeaningful(entry: DiaryEntry): Boolean =
        entry.title.isNotBlank() || entry.hasText || entry.attachments.isNotEmpty()

    /**
     * Cancels the debounce and writes the current draft immediately.
     * Returns true when the draft is safely persisted (or nothing was pending).
     */
    private suspend fun flushPendingSave(): Boolean {
        autosaveJob?.cancel()
        autosaveJob = null
        if (!hasUnsavedEdits) return true
        val state = _uiState.value
        val existing = currentEntry
        val updated = buildEntry(state, existing)
        hasUnsavedEdits = false
        if (isMeaningful(updated)) {
            return try {
                diaryRepository.saveEntry(updated)
                currentEntry = updated
                true
            } catch (_: Exception) {
                hasUnsavedEdits = true
                false
            }
        }
        return true
    }

    /**
     * Explicit save action from the diary's Save button. Persists the current
     * draft locally and requests exactly one cloud backup snapshot. Typing alone
     * never triggers this path, so earlier backups are never touched implicitly.
     */
    fun saveNow() {
        viewModelScope.launch {
            val saved = flushPendingSave()
            _uiState.value = _uiState.value.copy(
                saveState = if (saved) SaveState.SAVED else SaveState.ERROR
            )
            if (saved) onSaveRequested()
        }
    }

    private fun triggerAutosave() {
        autosaveJob?.cancel()
        _uiState.value = _uiState.value.copy(saveState = SaveState.SAVING)
        autosaveJob = viewModelScope.launch {
            delay(700) // 700ms debounce

            try {
                val state = _uiState.value
                val existing = currentEntry
                val updatedEntry = buildEntry(state, existing)

                if (isMeaningful(updatedEntry)) {
                    // Clear before the write: the database emits a change event
                    // that would otherwise look like an external overwrite.
                    hasUnsavedEdits = false
                    diaryRepository.saveEntry(updatedEntry)
                    currentEntry = updatedEntry
                    _uiState.value = _uiState.value.copy(saveState = SaveState.SAVED)
                } else {
                    hasUnsavedEdits = false
                    _uiState.value = _uiState.value.copy(saveState = SaveState.IDLE)
                }
            } catch (e: Exception) {
                hasUnsavedEdits = true
                _uiState.value = _uiState.value.copy(saveState = SaveState.ERROR)
            }
        }
    }

    // --- MEDIA ACTIONS ---

    fun startRecording() {
        mediaRepository.startRecording()
    }

    fun stopRecording() {
        viewModelScope.launch {
            val date = _uiState.value.selectedDate
            val newAttachment = mediaRepository.stopRecording(date)
            if (newAttachment != null) {
                triggerAutosave()
            }
        }
    }

    fun cancelRecording() {
        mediaRepository.cancelRecording()
    }

    fun playAudio(attachment: MediaAttachment) {
        mediaRepository.playAudio(attachment)
    }

    fun stopPlayback() {
        mediaRepository.stopPlayback()
    }

    fun onPhotoSelected(uri: Uri) {
        viewModelScope.launch {
            val date = _uiState.value.selectedDate
            val newPhoto = mediaRepository.savePhotoAttachment(date, uri)
            if (newPhoto != null) {
                triggerAutosave()
            }
        }
    }

    fun onCameraPhotoSelected(file: File) {
        viewModelScope.launch {
            val date = _uiState.value.selectedDate
            val newPhoto = mediaRepository.saveCameraPhoto(date, file)
            if (newPhoto != null) {
                triggerAutosave()
            }
        }
    }

    fun deleteAttachment(attachment: MediaAttachment) {
        viewModelScope.launch {
            mediaRepository.deleteAttachment(attachment)
            triggerAutosave()
        }
    }
}
