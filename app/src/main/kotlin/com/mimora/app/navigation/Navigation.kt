package com.mimora.app.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mimora.app.R
import com.mimora.app.core.di.AppContainer
import com.mimora.app.data.preferences.UserPreferences
import com.mimora.app.design.BackupRestoreTopBarActions
import com.mimora.app.design.MimoraShapes
import com.mimora.app.design.WritingCanvasTheme
import com.mimora.app.design.WritingCanvasThemeBackgroundContainer
import com.mimora.app.feature.backup.DriveAction
import com.mimora.app.feature.backup.rememberDriveAuthorizer
import com.mimora.app.feature.calendar.CalendarScreen
import com.mimora.app.feature.calendar.CalendarViewModel
import com.mimora.app.feature.home.HomeScreen
import com.mimora.app.feature.home.HomeViewModel
import com.mimora.app.feature.onboarding.OnboardingScreen
import com.mimora.app.feature.security.AppUnlockGate
import com.mimora.app.feature.security.SecureActionDialog
import com.mimora.app.feature.settings.REPO_URL
import com.mimora.app.feature.settings.StarPromptDialog
import com.mimora.app.feature.settings.openLink
import com.mimora.app.feature.settings.GoogleDriveRestoreDialog
import com.mimora.app.feature.settings.SettingsScreen
import com.mimora.app.feature.settings.SettingsViewModel
import com.mimora.app.feature.statistics.StatisticsScreen
import com.mimora.app.feature.statistics.StatisticsViewModel
import com.mimora.app.feature.tasks.TasksScreen
import com.mimora.app.feature.tasks.TasksViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

private data class PendingSecureAction(
    val title: String,
    val message: String,
    val confirmLabel: String = "Delete",
    val onConfirm: () -> Unit
)

private fun Screen.order(): Int = when (this) {
    Screen.Home -> 0
    Screen.Calendar -> 1
    Screen.Tasks -> 2
    Screen.Statistics -> 3
    Screen.Settings -> 4
}

@Composable
fun MainNavigation(
    container: AppContainer,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var previousScreen by remember { mutableStateOf<Screen>(Screen.Home) }

    val navigate: (Screen) -> Unit = { next ->
        if (next != currentScreen) {
            previousScreen = currentScreen
            currentScreen = next
        }
    }

    val appScope = rememberCoroutineScope()

    val settingsViewModel: SettingsViewModel = viewModel {
        SettingsViewModel(
            container.preferencesRepository,
            container.securityManager,
            container.backupManager,
            container.googleDriveAuth
        )
    }
    val onDriveAction = rememberDriveAuthorizer(settingsViewModel)
    val settingsUiState by settingsViewModel.uiState.collectAsState()

    // Cloud backup is explicit: only tapping the diary's Save button requests a
    // sync. Local autosave never calls this, and the upload layer skips unchanged
    // content, so typing cannot create duplicates or overwrite the last good backup.
    val onSaveRequested: () -> Unit = {
        val state = settingsViewModel.uiState.value
        if (state.preferences.googleDriveConnected && !state.isProcessingBackup) {
            onDriveAction(DriveAction.Sync)
        }
        Unit
    }

    val homeViewModel: HomeViewModel = viewModel {
        HomeViewModel(container.diaryRepository, container.mediaRepository, onSaveRequested)
    }
    val calendarViewModel: CalendarViewModel = viewModel {
        CalendarViewModel(container.diaryRepository)
    }
    val tasksViewModel: TasksViewModel = viewModel {
        TasksViewModel(container.taskRepository)
    }
    val statisticsViewModel: StatisticsViewModel = viewModel {
        StatisticsViewModel(container.statisticsRepository)
    }

    // Backup/restore feedback must be visible from every screen, including
    // onboarding and the unlock gate. One host lives at the root, pinned to the
    // top so it is never hidden by the bottom bar or the writing canvas.
    val snackbarHostState = remember { SnackbarHostState() }
    val backupMessage = settingsUiState.backupMessage
    LaunchedEffect(backupMessage) {
        if (backupMessage != null) {
            snackbarHostState.showSnackbar(backupMessage, duration = SnackbarDuration.Long)
            settingsViewModel.clearBackupMessage()
        }
    }

    // One-time, one-only star request three days after the first launch.
    LaunchedEffect(Unit) {
        container.preferencesRepository.recordFirstLaunchIfNeeded()
        settingsViewModel.maybeShowStarPrompt()
    }

    val topBarActions: @Composable () -> Unit = {
        BackupRestoreTopBarActions(
            onBackup = { onDriveAction(DriveAction.Sync) },
            onRestore = { onDriveAction(DriveAction.Restore) },
            isSyncing = settingsUiState.isProcessingBackup,
            syncOperation = settingsUiState.syncOperation
        )
    }

    var pendingSecureAction by remember { mutableStateOf<PendingSecureAction?>(null) }
    val requestSecureAction = { title: String, message: String, onConfirm: () -> Unit ->
        pendingSecureAction = PendingSecureAction(title, message, "Delete", onConfirm)
    }

    val storedPreferences by container.preferencesRepository.userPreferencesFlow
        .collectAsState(initial = null)
    val preferences = storedPreferences ?: UserPreferences()
    val preferencesLoaded = storedPreferences != null

    // Logout keeps all local data and the Drive connection. When a lock exists,
    // reuse the root-level PIN/fingerprint gate before leaving the session.
    val onLogout: () -> Unit = {
        if (preferences.appLockEnabled && preferences.pinHash.isNotBlank()) {
            pendingSecureAction = PendingSecureAction(
                title = "Confirm logout",
                message = "Your diary and Google Drive sign-in stay on this device. Verify to log out.",
                confirmLabel = "Log out",
                onConfirm = { settingsViewModel.logout() }
            )
        } else {
            settingsViewModel.logout()
        }
    }

    // The app lock is active only after onboarding, and relocks whenever the app
    // leaves the foreground. Starting locked until preferences arrive prevents a
    // cold start from briefly exposing the diary before the lock is known.
    val requiresUnlock = preferencesLoaded &&
        preferences.hasCompletedOnboarding &&
        preferences.appLockEnabled &&
        preferences.pinHash.isNotBlank()
    var isUnlocked by remember { mutableStateOf(false) }
    LaunchedEffect(preferencesLoaded, requiresUnlock) {
        isUnlocked = preferencesLoaded && !requiresUnlock
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, requiresUnlock) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && requiresUnlock) {
                isUnlocked = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val writingTheme = remember(preferences.writingThemeIndex) {
        WritingCanvasTheme.fromIndex(preferences.writingThemeIndex)
    }

    WritingCanvasThemeBackgroundContainer(
        theme = writingTheme,
        isDarkTheme = isDarkTheme
    ) {
        // Keep the gradient edge-to-edge, but reserve space for the status bar
        // (and any cutout) so headers are never hidden under it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
        ) {
            if (requiresUnlock && !isUnlocked) {
                AppUnlockGate(
                    biometricEnabled = preferences.biometricEnabled,
                    pinHash = preferences.pinHash,
                    securityManager = container.securityManager,
                    onUnlocked = { isUnlocked = true }
                )
            } else if (!preferences.hasCompletedOnboarding) {
                OnboardingScreen(
                    viewModel = settingsViewModel,
                    onOnboardingComplete = { navigate(Screen.Home) },
                    hasExistingJournal = preferences.hasJournal,
                    // No extra gate here: setting onboarding complete flips
                    // requiresUnlock back on, so the existing AppUnlockGate
                    // handles PIN/fingerprint before the diary is shown.
                    onResumeDiary = {
                        appScope.launch {
                            container.preferencesRepository.setCompletedOnboarding(true)
                        }
                        navigate(Screen.Home)
                    }
                )
            } else {
                Scaffold(
                    modifier = modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
                    bottomBar = {
                        AnimatedVisibility(
                            visible = true,
                            enter = slideInVertically(initialOffsetY = { it }),
                            exit = slideOutVertically(targetOffsetY = { it })
                        ) {
                            StitchFloatingBottomBar(
                                currentScreen = currentScreen,
                                onNavigate = navigate
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                val forward = targetState.order() >= previousScreen.order()
                                if (forward) {
                                    (slideInHorizontally(initialOffsetX = { it / 10 }) + fadeIn(tween(220))) togetherWith
                                        (slideOutHorizontally(targetOffsetX = { -it / 10 }) + fadeOut(tween(160)))
                                } else {
                                    (slideInHorizontally(initialOffsetX = { -it / 10 }) + fadeIn(tween(220))) togetherWith
                                        (slideOutHorizontally(targetOffsetX = { it / 10 }) + fadeOut(tween(160)))
                                }
                            },
                            label = "screen-transition"
                        ) { screen ->
                            when (screen) {
                                Screen.Home -> HomeScreen(
                                    viewModel = homeViewModel,
                                    writingTheme = writingTheme,
                                    isDarkTheme = isDarkTheme,
                                    googleDriveConnected = preferences.googleDriveConnected,
                                    topBarActions = topBarActions,
                                    onRequestSecureAction = requestSecureAction
                                )
                                Screen.Calendar -> CalendarScreen(
                                    viewModel = calendarViewModel,
                                    onDateClick = { date: LocalDate ->
                                        homeViewModel.selectDate(date)
                                        navigate(Screen.Home)
                                    },
                                    topBarActions = topBarActions
                                )
                                Screen.Tasks -> TasksScreen(
                                    viewModel = tasksViewModel,
                                    topBarActions = topBarActions,
                                    onRequestSecureAction = requestSecureAction
                                )
                                Screen.Statistics -> StatisticsScreen(
                                    viewModel = statisticsViewModel,
                                    topBarActions = topBarActions
                                )
                                Screen.Settings -> SettingsScreen(
                                    viewModel = settingsViewModel,
                                    topBarActions = topBarActions,
                                    onLogout = onLogout
                                )
                            }
                        }
                    }
                }
            }

            // Restore is available on first install too, not only after
            // onboarding. The key field is always blank — this device's own key
            // is never displayed as the answer for a different backup.
            if (settingsUiState.showGoogleDriveRestoreDialog) {
                GoogleDriveRestoreDialog(
                    files = settingsUiState.googleDriveCloudFiles,
                    isLoading = settingsUiState.isLoadingDriveFiles,
                    isProcessing = settingsUiState.isProcessingBackup,
                    onRestore = { fileId, phrase ->
                        onDriveAction(DriveAction.RestoreFile(fileId, phrase))
                    },
                    onRefresh = { onDriveAction(DriveAction.Refresh) },
                    onDismiss = { settingsViewModel.closeGoogleDriveRestoreDialog() }
                )
            }

            pendingSecureAction?.let { action ->
                SecureActionDialog(
                    title = action.title,
                    message = action.message,
                    confirmLabel = action.confirmLabel,
                    biometricEnabled = preferences.biometricEnabled,
                    pinHash = preferences.pinHash,
                    securityManager = container.securityManager,
                    onPinCreated = { hash, continueAction ->
                        appScope.launch {
                            container.preferencesRepository.setPinHash(hash)
                            continueAction()
                        }
                    },
                    onAuthenticated = {
                        action.onConfirm()
                        pendingSecureAction = null
                    },
                    onDismiss = { pendingSecureAction = null }
                )
            }

            if (settingsUiState.showStarPrompt) {
                StarPromptDialog(
                    onStar = { openLink(context, REPO_URL) },
                    onDismiss = { settingsViewModel.dismissStarPrompt() }
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .zIndex(10f)
            ) { data ->
                Snackbar(
                    data,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MimoraShapes.medium
                )
            }
        }
    }
}

/**
 * Stitch Floating Pill Bottom Navigation Bar.
 * The selected pill slides horizontally between tabs.
 */
@Composable
private fun StitchFloatingBottomBar(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit
) {
    val density = LocalDensity.current
    var barWidthPx by remember { mutableIntStateOf(0) }
    val tabWidth = barWidthPx / 5f
    val indicatorOffset by animateDpAsState(
        targetValue = with(density) { (tabWidth * currentScreen.order()).toDp() },
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 420f),
        label = "navIndicatorSlide"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .onSizeChanged { barWidthPx = it.width },
        shape = MimoraShapes.full,
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.95f),
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(with(density) { tabWidth.toDp() })
                    .height(52.dp)
                    .clip(MimoraShapes.full)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f))
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StitchNavItem(
                    iconRes = R.drawable.ic_diary,
                    label = "Diary",
                    isSelected = currentScreen == Screen.Home,
                    onClick = { onNavigate(Screen.Home) },
                    modifier = Modifier.weight(1f)
                )
                StitchNavItem(
                    iconRes = R.drawable.ic_calendar,
                    label = "Calendar",
                    isSelected = currentScreen == Screen.Calendar,
                    onClick = { onNavigate(Screen.Calendar) },
                    modifier = Modifier.weight(1f)
                )
                StitchNavItem(
                    iconRes = R.drawable.ic_task,
                    label = "Tasks",
                    isSelected = currentScreen == Screen.Tasks,
                    onClick = { onNavigate(Screen.Tasks) },
                    modifier = Modifier.weight(1f)
                )
                StitchNavItem(
                    iconRes = R.drawable.ic_stats,
                    label = "Stats",
                    isSelected = currentScreen == Screen.Statistics,
                    onClick = { onNavigate(Screen.Statistics) },
                    modifier = Modifier.weight(1f)
                )
                StitchNavItem(
                    iconRes = R.drawable.ic_settings,
                    label = "Settings",
                    isSelected = currentScreen == Screen.Settings,
                    onClick = { onNavigate(Screen.Settings) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StitchNavItem(
    iconRes: Int,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconTint by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
        },
        animationSpec = tween(200),
        label = "navIconTint"
    )
    Box(
        modifier = modifier
            .clip(MimoraShapes.full)
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = iconTint
            )
        }
    }
}
