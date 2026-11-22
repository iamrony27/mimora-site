package com.mimora.app.data.repository

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import com.mimora.app.core.model.MediaAttachment
import com.mimora.app.core.model.MediaType
import com.mimora.app.database.MimoraDatabaseHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.UUID

sealed interface RecordingState {
    object Idle : RecordingState
    data class Recording(val durationSeconds: Int) : RecordingState
    data class Error(val message: String) : RecordingState
}

sealed interface PlaybackState {
    object Idle : PlaybackState
    data class Playing(val attachmentId: String, val currentPositionMs: Int, val durationMs: Int) : PlaybackState
    data class Paused(val attachmentId: String) : PlaybackState
}

class MediaRepository(
    private val context: Context,
    private val db: MimoraDatabaseHelper
) {
    private val photosDir = File(context.filesDir, "photos").apply { if (!exists()) mkdirs() }
    private val voiceDir = File(context.filesDir, "voice_notes").apply { if (!exists()) mkdirs() }

    private var recorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null
    private var recordingStartTime = 0L
    private var recordingTimerJob: Job? = null

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var playbackTimerJob: Job? = null
    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)

    // --- AUDIO RECORDING ---

    fun startRecording(): Boolean {
        return try {
            stopPlayback()
            val file = File(voiceDir, "voice_${UUID.randomUUID()}.m4a")
            currentRecordingFile = file

            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioSamplingRate(44100)
            rec.setAudioEncodingBitRate(96000)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()

            recorder = rec
            recordingStartTime = System.currentTimeMillis()
            _recordingState.value = RecordingState.Recording(0)

            recordingTimerJob?.cancel()
            recordingTimerJob = scope.launch {
                while (isActive) {
                    delay(1000)
                    val elapsed = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
                    _recordingState.value = RecordingState.Recording(elapsed)
                }
            }
            true
        } catch (e: Exception) {
            _recordingState.value = RecordingState.Error(e.message ?: "Failed to start recording")
            cleanupRecorder()
            false
        }
    }

    suspend fun stopRecording(entryDate: LocalDate): MediaAttachment? = withContext(Dispatchers.IO) {
        recordingTimerJob?.cancel()
        val file = currentRecordingFile
        val duration = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()

        try {
            recorder?.stop()
        } catch (e: Exception) {
            file?.delete()
            cleanupRecorder()
            _recordingState.value = RecordingState.Idle
            return@withContext null
        }
        cleanupRecorder()
        _recordingState.value = RecordingState.Idle

        if (file != null && file.exists() && duration >= 1) {
            val attachment = MediaAttachment(
                id = UUID.randomUUID().toString(),
                entryDate = entryDate,
                type = MediaType.VOICE,
                filePath = file.absolutePath,
                durationSeconds = duration
            )
            db.addAttachment(attachment)
            attachment
        } else {
            file?.delete()
            null
        }
    }

    fun cancelRecording() {
        recordingTimerJob?.cancel()
        try {
            recorder?.stop()
        } catch (_: Exception) {}
        cleanupRecorder()
        currentRecordingFile?.delete()
        currentRecordingFile = null
        _recordingState.value = RecordingState.Idle
    }

    private fun cleanupRecorder() {
        try {
            recorder?.release()
        } catch (_: Exception) {}
        recorder = null
    }

    // --- AUDIO PLAYBACK ---

    fun playAudio(attachment: MediaAttachment) {
        val file = File(attachment.filePath)
        if (!file.exists()) return

        stopPlayback()
        try {
            val mp = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
            }
            mediaPlayer = mp
            val totalDuration = mp.duration

            _playbackState.value = PlaybackState.Playing(attachment.id, 0, totalDuration)

            playbackTimerJob?.cancel()
            playbackTimerJob = scope.launch {
                while (isActive && mp.isPlaying) {
                    _playbackState.value = PlaybackState.Playing(attachment.id, mp.currentPosition, totalDuration)
                    delay(250)
                }
                _playbackState.value = PlaybackState.Idle
            }

            mp.setOnCompletionListener {
                stopPlayback()
            }
        } catch (e: Exception) {
            stopPlayback()
        }
    }

    fun stopPlayback() {
        playbackTimerJob?.cancel()
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        _playbackState.value = PlaybackState.Idle
    }

    // --- PHOTO MANAGEMENT ---

    suspend fun savePhotoAttachment(entryDate: LocalDate, sourceUri: Uri): MediaAttachment? = withContext(Dispatchers.IO) {
        try {
            val targetFile = File(photosDir, "photo_${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (targetFile.exists() && targetFile.length() > 0) {
                val attachment = MediaAttachment(
                    id = UUID.randomUUID().toString(),
                    entryDate = entryDate,
                    type = MediaType.PHOTO,
                    filePath = targetFile.absolutePath
                )
                db.addAttachment(attachment)
                attachment
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Camera output is already a private app file. Reading it directly avoids
     * relying on a one-shot content-provider grant after the camera closes.
     */
    suspend fun saveCameraPhoto(entryDate: LocalDate, sourceFile: File): MediaAttachment? = withContext(Dispatchers.IO) {
        try {
            val targetFile = File(photosDir, "photo_${UUID.randomUUID()}.jpg")
            sourceFile.copyTo(targetFile, overwrite = true)
            if (targetFile.exists() && targetFile.length() > 0) {
                val attachment = MediaAttachment(
                    id = UUID.randomUUID().toString(),
                    entryDate = entryDate,
                    type = MediaType.PHOTO,
                    filePath = targetFile.absolutePath
                )
                db.addAttachment(attachment)
                sourceFile.delete()
                attachment
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun deleteAttachment(attachment: MediaAttachment) = withContext(Dispatchers.IO) {
        try {
            val file = File(attachment.filePath)
            if (file.exists()) file.delete()
        } catch (_: Exception) {}
        db.deleteAttachment(attachment.id)
    }
}
