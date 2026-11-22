package com.mimora.app.feature.home

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mimora.app.R
import com.mimora.app.core.model.MediaAttachment
import com.mimora.app.core.model.MediaType
import com.mimora.app.data.repository.PlaybackState
import com.mimora.app.data.repository.RecordingState
import com.mimora.app.design.DailyMood
import com.mimora.app.design.MimoraIconButton
import com.mimora.app.design.MimoraShapes
import com.mimora.app.design.MimoraSpacing
import com.mimora.app.design.SaveStateIndicator
import com.mimora.app.design.WritingCanvasTheme
import com.mimora.app.design.chatWallpaperPattern
import com.mimora.app.design.StitchPill
import com.mimora.app.design.StitchPillWithIcon
import com.mimora.app.design.WaveformVisualizer
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    writingTheme: WritingCanvasTheme = WritingCanvasTheme.LAVENDER,
    isDarkTheme: Boolean = false,
    topBarActions: @Composable () -> Unit = {},
    onRequestSecureAction: (String, String, () -> Unit) -> Unit = { _, _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val isRecording = recordingState is RecordingState.Recording
    val recordingSeconds = (recordingState as? RecordingState.Recording)?.durationSeconds ?: 0

    val context = LocalContext.current

    // Media permissions
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (isGranted) {
            viewModel.startRecording()
        }
    }

    fun toggleRecording() {
        if (isRecording) {
            viewModel.stopRecording()
        } else if (hasMicPermission) {
            viewModel.startRecording()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Photo picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.onPhotoSelected(it) }
    }

    // Camera capture — request the runtime permission before starting the
    // ACTION_IMAGE_CAPTURE intent, otherwise OEM camera apps reject the launch.
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var tempCameraFile by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val capturedFile = tempCameraFile
            if (capturedFile != null && capturedFile.exists() && capturedFile.length() > 0) {
                viewModel.onCameraPhotoSelected(capturedFile)
            } else {
                tempCameraUri?.let { viewModel.onPhotoSelected(it) }
            }
        } else {
            Toast.makeText(context, "Photo capture was cancelled.", Toast.LENGTH_SHORT).show()
        }
        tempCameraUri = null
        tempCameraFile = null
    }

    fun launchCamera() {
        try {
            val photosDir = File(context.filesDir, "photos").apply { if (!exists()) mkdirs() }
            val tempFile = File.createTempFile("temp_cam_", ".jpg", photosDir)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
            tempCameraFile = tempFile
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            tempCameraFile = null
            tempCameraUri = null
            Toast.makeText(context, "Camera is not available on this device.", Toast.LENGTH_SHORT).show()
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(context, "Camera permission is needed to capture a photo.", Toast.LENGTH_SHORT).show()
        }
    }

    // Live word count and reading time
    val wordCount = remember(uiState.content) {
        if (uiState.content.isBlank()) 0
        else uiState.content.trim().split("\\s+".toRegex()).size
    }
    val readingTimeMin = remember(wordCount) {
        maxOf(1, (wordCount / 150) + if (wordCount > 0) 1 else 0)
    }

    // Follow the user's selected theme, not the system setting. Otherwise the
    // writing canvas turns black while the rest of the app stays in light mode.
    val isDark = isDarkTheme
    // Kept in the ViewModel so the selected mood survives tab switches.
    val currentMood = DailyMood.fromId(uiState.moodId)
    var showMoodDialog by remember { mutableStateOf(false) }
    var fullscreenPhoto by remember { mutableStateOf<MediaAttachment?>(null) }

    val canvasColors = if (isDark) writingTheme.darkCanvasColors else writingTheme.lightCanvasColors
    val canvasTopColor by animateColorAsState(
        targetValue = canvasColors[0],
        animationSpec = tween(durationMillis = 450),
        label = "canvasTop"
    )
    val canvasBottomColor by animateColorAsState(
        targetValue = canvasColors[1],
        animationSpec = tween(durationMillis = 450),
        label = "canvasBottom"
    )
    val canvasBrush = remember(canvasTopColor, canvasBottomColor) {
        Brush.verticalGradient(listOf(canvasTopColor, canvasBottomColor))
    }
    val fieldContainerColor = writingTheme.accentColor.copy(alpha = if (isDark) 0.14f else 0.08f)
    val fieldBorderColor = writingTheme.accentColor.copy(alpha = 0.32f)

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = MimoraSpacing.normal)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 104.dp)
        ) {
        Spacer(modifier = Modifier.height(MimoraSpacing.small))

        // --- STITCH TOP APP BRAND HEADER ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MimoraSpacing.small),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(MimoraShapes.small)
                        // The MIMORA brand mark is fixed. Writing themes colour the
                        // canvas and accents, never the app's identity/icon.
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF6750A4),
                                    Color(0xFF8370C4)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_diary),
                        contentDescription = "MIMORA Logo",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "MIMORA",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "Diary",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Private Vault pill
                Box(
                    modifier = Modifier
                        .clip(MimoraShapes.full)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = "Vault Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                topBarActions()
            }
        }

        // --- DATE NAVIGATION & STATUS ROW ---
        DateNavigationRow(
            selectedDate = uiState.selectedDate,
            canGoForward = uiState.canGoForward,
            isToday = uiState.isToday,
            saveState = uiState.saveState,
            onPreviousClick = { viewModel.goToPreviousDay() },
            onNextClick = { viewModel.goToNextDay() },
            onTodayClick = { viewModel.goToToday() }
        )

        // --- MOOD SELECTOR ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MimoraSpacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StitchPillWithIcon(
                iconRes = currentMood.iconRes,
                text = currentMood.title,
                containerColor = currentMood.accentColor.copy(alpha = 0.16f),
                contentColor = currentMood.accentColor,
                onClick = { showMoodDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(MimoraSpacing.small))

        // Active Voice Recording Banner
        AnimatedVisibility(
            visible = recordingState is RecordingState.Recording,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            if (recordingState is RecordingState.Recording) {
                val duration = (recordingState as RecordingState.Recording).durationSeconds
                ActiveRecordingBanner(
                    durationSeconds = duration,
                    onStop = { viewModel.stopRecording() },
                    onCancel = { viewModel.cancelRecording() }
                )
            }
        }

        Spacer(modifier = Modifier.height(MimoraSpacing.small))

        // --- STITCH WRITING CANVAS CARD ("Serene Sanctuary" Cloth-Bound Journal Card) ---
        Card(
            shape = MimoraShapes.card,
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(canvasBrush, MimoraShapes.card)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // WhatsApp-style faint doodles behind the text fields.
                    .chatWallpaperPattern(writingTheme.accentColor)
                    .padding(MimoraSpacing.large)
            ) {
                // Colorful title field with its own placeholder hint.
                OutlinedTextField(
                    value = uiState.title,
                    onValueChange = { viewModel.onTitleChanged(it) },
                    placeholder = {
                        Text(
                            text = "Title your memory...",
                            style = MaterialTheme.typography.headlineSmall,
                            color = writingTheme.accentColor.copy(alpha = 0.55f),
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = MimoraShapes.medium,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = fieldContainerColor,
                        unfocusedContainerColor = fieldContainerColor,
                        focusedIndicatorColor = writingTheme.accentColor.copy(alpha = 0.65f),
                        unfocusedIndicatorColor = fieldBorderColor,
                        cursorColor = writingTheme.accentColor
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Colorful body field with its own placeholder hint.
                OutlinedTextField(
                    value = uiState.content,
                    onValueChange = { viewModel.onContentChanged(it) },
                    placeholder = {
                        Text(
                            text = "Write what you felt, noticed, and want to remember...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = writingTheme.accentColor.copy(alpha = 0.55f),
                            lineHeight = 26.sp
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = 26.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = MimoraShapes.medium,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = fieldContainerColor,
                        unfocusedContainerColor = fieldContainerColor,
                        focusedIndicatorColor = writingTheme.accentColor.copy(alpha = 0.65f),
                        unfocusedIndicatorColor = fieldBorderColor,
                        cursorColor = writingTheme.accentColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 150.dp)
                )

                Spacer(modifier = Modifier.height(MimoraSpacing.medium))

                Text(
                    text = "ADD A PHOTO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = writingTheme.accentColor,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EnrichActionCard(
                        iconRes = R.drawable.ic_camera,
                        title = "Camera",
                        subtitle = "Capture a photo",
                        accentColor = writingTheme.accentColor,
                        containerColor = writingTheme.accentColor.copy(alpha = if (isDark) 0.18f else 0.10f),
                        onClick = {
                            if (hasCameraPermission) {
                                launchCamera()
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(116.dp)
                    )

                    EnrichActionCard(
                        iconRes = R.drawable.ic_photo,
                        title = "Gallery",
                        subtitle = "Import a photo",
                        accentColor = writingTheme.accentColor,
                        containerColor = writingTheme.accentColor.copy(alpha = if (isDark) 0.18f else 0.10f),
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(116.dp)
                    )
                }

                // --- ATTACHED AUDIO WAVEFORM CARDS ---
                val voiceAttachments = uiState.attachments.filter { it.type == MediaType.VOICE }
                if (voiceAttachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(MimoraSpacing.medium))
                    val currentPlayback = playbackState
                    voiceAttachments.forEach { voice ->
                        val isPlaying = currentPlayback is PlaybackState.Playing && currentPlayback.attachmentId == voice.id
                        AudioWaveformCard(
                            attachment = voice,
                            isPlaying = isPlaying,
                            onPlay = { viewModel.playAudio(voice) },
                            onStop = { viewModel.stopPlayback() },
                            onDelete = {
                                onRequestSecureAction(
                                    "Delete voice note?",
                                    "This recording will be permanently removed from this memory.",
                                    { viewModel.deleteAttachment(voice) }
                                )
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // --- ATTACHED PHOTO THUMBNAILS GRID ---
                val photoAttachments = uiState.attachments.filter { it.type == MediaType.PHOTO }
                if (photoAttachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(MimoraSpacing.medium))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(photoAttachments, key = { it.id }) { photo ->
                            PhotoThumbnailCard(
                                photo = photo,
                                onOpen = { fullscreenPhoto = photo },
                                onDelete = {
                                    onRequestSecureAction(
                                        "Delete photo?",
                                        "This photo will be permanently removed from this memory.",
                                        { viewModel.deleteAttachment(photo) }
                                    )
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(MimoraSpacing.large))

                // --- CARD FOOTER METADATA (Stitch Layout) ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$wordCount words · $readingTimeMin min read",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_cloud_backup),
                            contentDescription = "Encrypted",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "End-to-End Encrypted",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(MimoraSpacing.doubleLarge))
    }

        FloatingRecordButton(
            isRecording = isRecording,
            durationSeconds = recordingSeconds,
            accentColor = writingTheme.accentColor,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 16.dp),
            onClick = { toggleRecording() }
        )
    }

    if (showMoodDialog) {
        DailyMoodDialog(
            selectedMood = currentMood,
            onMoodSelected = { viewModel.onMoodSelected(it) },
            onDismiss = { showMoodDialog = false }
        )
    }

    fullscreenPhoto?.let { photo ->
        FullscreenPhotoDialog(
            photo = photo,
            onDelete = {
                onRequestSecureAction(
                    "Delete photo?",
                    "This photo will be removed from this memory. Confirm with your fingerprint or PIN.",
                    {
                        viewModel.deleteAttachment(photo)
                        fullscreenPhoto = null
                    }
                )
            },
            onDismiss = { fullscreenPhoto = null }
        )
    }
}

/**
 * Top Date Navigation Row with Stitch styling.
 */
@Composable
private fun DateNavigationRow(
    selectedDate: LocalDate,
    canGoForward: Boolean,
    isToday: Boolean,
    saveState: com.mimora.app.design.SaveState,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onTodayClick: () -> Unit
) {
    val formatter = remember { DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.getDefault()) }
    val formattedDate = remember(selectedDate) { selectedDate.format(formatter) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MimoraIconButton(
                painter = painterResource(R.drawable.ic_calendar),
                contentDescription = "Previous Day",
                onClick = onPreviousClick
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(MimoraShapes.full)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                val shortFormatter = remember { DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()) }
                val displayDate = remember(selectedDate) { selectedDate.format(shortFormatter) }

                Text(
                    text = displayDate,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isToday) {
                    Box(
                        modifier = Modifier
                            .clip(MimoraShapes.full)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Today",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (!isToday) {
                Box(
                    modifier = Modifier
                        .clip(MimoraShapes.full)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { onTodayClick() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Go to Today",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            SaveStateIndicator(state = saveState)
            Spacer(modifier = Modifier.width(4.dp))
            MimoraIconButton(
                painter = painterResource(R.drawable.ic_edit),
                contentDescription = "Next Day",
                enabled = canGoForward,
                onClick = onNextClick
            )
        }
    }
}

/**
 * Audio Waveform Card embedded directly into the writing canvas card.
 */
@Composable
private fun AudioWaveformCard(
    attachment: MediaAttachment,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = MimoraShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Purple circular play/pause button
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { if (isPlaying) onStop() else onPlay() }
                        .semantics {
                            contentDescription = if (isPlaying) "Stop voice note" else "Play voice note"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(
                            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                        ),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_voice_note),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Voice note",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    WaveformVisualizer(isPlaying = isPlaying)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatDuration(attachment.durationSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .clip(MimoraShapes.full)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                        .semantics { contentDescription = "Delete voice note" }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Photo thumbnail. Tapping the image opens a fullscreen preview; the delete
 * badge uses the same modern icon as every other destructive action.
 */
@Composable
private fun PhotoThumbnailCard(
    photo: MediaAttachment,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val file = remember(photo.filePath) { File(photo.filePath) }
    val bitmap = remember(file) {
        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    Box(
        modifier = Modifier
            .size(width = 110.dp, height = 100.dp)
            .clip(MimoraShapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClickLabel = "View photo full screen") { onOpen() }
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Attached photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_photo),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.92f))
                .clickable(onClickLabel = "Delete photo") { onDelete() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = "Delete photo",
                tint = Color.White,
                modifier = Modifier.size(13.dp)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_expand),
                contentDescription = "Expand photo",
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

/**
 * Immersive fullscreen photo preview with protected delete and close controls.
 */
@Composable
private fun FullscreenPhotoDialog(
    photo: MediaAttachment,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val file = remember(photo.filePath) { File(photo.filePath) }
    val bitmap = remember(file) {
        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0910))
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Fullscreen attached photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "This photo is no longer available.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(horizontal = MimoraSpacing.normal, vertical = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(MimoraSpacing.small)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.92f))
                        .clickable(onClickLabel = "Delete photo") { onDelete() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = "Delete photo",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(onClickLabel = "Close photo") { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "Close photo",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Thumb-reachable floating record control. It stays fixed while the journal
 * scrolls, so starting or stopping a voice note never requires hunting through
 * the writing canvas.
 */
@Composable
private fun FloatingRecordButton(
    isRecording: Boolean,
    durationSeconds: Int,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val label = if (isRecording) {
        "Stop voice recording at ${formatDuration(durationSeconds)}"
    } else {
        "Start voice recording"
    }

    FloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = if (isRecording) MaterialTheme.colorScheme.error else accentColor,
        contentColor = Color.White,
        modifier = modifier
            .size(64.dp)
            .semantics { contentDescription = label }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.ic_voice_note),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            if (isRecording) {
                Text(
                    text = formatDuration(durationSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Active recording banner during audio capture.
 */
@Composable
private fun ActiveRecordingBanner(
    durationSeconds: Int,
    onStop: () -> Unit,
    onCancel: () -> Unit
) {
    val timeFormatted = formatDuration(durationSeconds)

    Card(
        shape = MimoraShapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MimoraSpacing.small)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MimoraSpacing.normal, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RecordingPulseDot(color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Recording $timeFormatted",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    RecordingBars(color = MaterialTheme.colorScheme.error)
                }
            }

            Row {
                MimoraIconButton(
                    painter = painterResource(R.drawable.ic_reminder),
                    contentDescription = "Cancel Recording",
                    onClick = onCancel,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                MimoraIconButton(
                    painter = painterResource(R.drawable.ic_voice_note),
                    contentDescription = "Finish Recording",
                    onClick = onStop,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatDuration(seconds: Int): String =
    String.format("%02d:%02d", seconds / 60, seconds % 60)

/**
 * Modern Daily Mood Dialog with SVG vector icons and zero borders/shadows.
 */
@Composable
private fun DailyMoodDialog(
    selectedMood: DailyMood,
    onMoodSelected: (DailyMood) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Mood of the Day",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Select the mood that matches your reflection. Your writing color stays as you chose it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DailyMood.entries.forEach { mood ->
                    val isSelected = selectedMood == mood
                    Surface(
                        shape = MimoraShapes.card,
                        color = if (isSelected) mood.accentColor.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onMoodSelected(mood)
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(mood.accentColor.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(mood.iconRes),
                                    contentDescription = mood.title,
                                    tint = mood.accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = mood.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Text(
                                    text = "✓",
                                    color = mood.accentColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
