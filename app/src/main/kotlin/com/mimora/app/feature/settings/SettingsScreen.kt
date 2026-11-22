package com.mimora.app.feature.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mimora.app.R
import com.mimora.app.core.model.DateRules
import com.mimora.app.data.preferences.ThemeMode
import com.mimora.app.design.DriveStatusIndicator
import com.mimora.app.design.MimoraShapes
import com.mimora.app.design.MimoraSpacing
import com.mimora.app.design.WritingCanvasTheme
import com.mimora.app.design.StitchPill
import com.mimora.app.design.StitchSegmentedControl
import com.mimora.app.feature.security.rememberFingerprintAvailable
import com.mimora.app.feature.backup.DriveAction
import com.mimora.app.feature.backup.rememberDriveAuthorizer
import com.mimora.app.feature.backup.GoogleDriveFile
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    topBarActions: @Composable () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val prefs = uiState.preferences

    var showPinDialog by remember { mutableStateOf(false) }
    var showDeveloperNote by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val fingerprintAvailable = rememberFingerprintAvailable()

    // Temporary storage for backup bytes pending user destination selection in SAF
    var pendingExportBytes by remember { mutableStateOf<ByteArray?>(null) }

    // --- STORAGE ACCESS FRAMEWORK (SAF) LAUNCHERS FOR GOOGLE DRIVE ---
    // 1. Export Document: Opens Android System File Picker (supports Google Drive natively)
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        uri?.let { fileUri ->
            pendingExportBytes?.let { bytes ->
                viewModel.saveBackupToUri(context, fileUri, bytes)
                pendingExportBytes = null
            }
        }
    }

    // 2. Open Document: Allows picking a .mimora backup file from Google Drive or local storage
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { fileUri ->
            viewModel.onBackupFilePickedForRestore(context, fileUri)
        }
    }

    // Google sign-in is shared with the top bar so consent works the same way.
    val onDriveAction = rememberDriveAuthorizer(viewModel)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MimoraSpacing.normal),
        contentPadding = PaddingValues(vertical = MimoraSpacing.normal)
    ) {
        // --- STITCH HEADER ---
        item {
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
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = "MIMORA Settings Logo",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "MIMORA",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Settings & Personalization",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DriveStatusIndicator(connected = prefs.googleDriveConnected)
                    topBarActions()
                }
            }
            Spacer(modifier = Modifier.height(MimoraSpacing.medium))
        }

        // --- PERSONAL JOURNAL PROFILE CARD ---
        item {
            Card(
                shape = MimoraShapes.card,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MimoraSpacing.large),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MimoraSpacing.medium)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(MimoraShapes.full)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_diary),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Personal Journal",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            DriveStatusIndicator(connected = prefs.googleDriveConnected)
                        }
                        Text(
                            text = if (prefs.hasAcknowledgedKeyWarning) {
                                "Master Vault Key: Secured Offline"
                            } else {
                                "Private Vault: Active · Local Keystore"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(MimoraSpacing.large))
        }

        // --- APPEARANCE & STYLING ---
        item {
            SettingsSectionHeader(title = "Appearance & Styling")

            Card(
                shape = MimoraShapes.card,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(MimoraSpacing.large)) {
                    Text(
                        text = "Theme Mode",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(MimoraSpacing.small))

                    val themeOptions = listOf("System", "Light", "Dark")
                    val selectedThemeIdx = when (prefs.themeMode) {
                        ThemeMode.SYSTEM -> 0
                        ThemeMode.LIGHT -> 1
                        ThemeMode.DARK -> 2
                    }
                    StitchSegmentedControl(
                        items = themeOptions,
                        selectedIndex = selectedThemeIdx,
                        onItemSelected = { idx ->
                            val mode = when (idx) {
                                1 -> ThemeMode.LIGHT
                                2 -> ThemeMode.DARK
                                else -> ThemeMode.SYSTEM
                            }
                            viewModel.setThemeMode(mode)
                        },
                        itemLabel = { it }
                    )

                    Spacer(modifier = Modifier.height(MimoraSpacing.large))

                    Text(
                        text = "Writing Canvas Color",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Choose the color of your writing page.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(MimoraSpacing.small))

                    val selectedThemeIndex = prefs.writingThemeIndex.coerceIn(
                        0,
                        WritingCanvasTheme.entries.lastIndex
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WritingCanvasTheme.entries.forEachIndexed { index, theme ->
                            val selected = index == selectedThemeIndex
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(MimoraShapes.medium)
                                    .background(
                                        if (selected) theme.accentColor.copy(alpha = 0.12f)
                                        else Color.Transparent
                                    )
                                    .clickable { viewModel.setWritingTheme(index) }
                                    .semantics {
                                        contentDescription = "Use ${theme.displayName} writing color"
                                    }
                                    .padding(vertical = 10.dp, horizontal = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(theme.swatchColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
                                        Text(
                                            text = "✓",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = theme.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) theme.accentColor
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(MimoraSpacing.large))
        }

        // --- SECURITY & PRIVACY ---
        item {
            SettingsSectionHeader(title = "Security & Privacy")

            Card(
                shape = MimoraShapes.card,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(MimoraSpacing.large)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "App Lock (PIN)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Require PIN or fingerprint to open your diary",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = prefs.appLockEnabled,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    showPinDialog = true
                                } else {
                                    viewModel.toggleAppLock(enabled = false)
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(MimoraSpacing.normal))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_fingerprint),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Fingerprint unlock",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = if (fingerprintAvailable)
                                    "Use your enrolled fingerprint instead of typing a PIN"
                                else
                                    "No fingerprint is enrolled on this device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = prefs.biometricEnabled && fingerprintAvailable,
                            enabled = fingerprintAvailable && prefs.appLockEnabled && prefs.pinHash.isNotBlank(),
                            onCheckedChange = { isChecked ->
                                if (!fingerprintAvailable) {
                                    Toast.makeText(
                                        context,
                                        "No fingerprint is enrolled on this device.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    viewModel.setBiometricEnabled(isChecked)
                                }
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(MimoraSpacing.large))
        }

        // --- MASTER PRIVATE RECOVERY KEY CARD ---
        item {
            SettingsSectionHeader(title = "Vault Master Private Key")

            Card(
                shape = MimoraShapes.card,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(MimoraSpacing.large)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_security),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Master Encryption Key",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "24-Word Offline Private Vault Key",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (prefs.hasAcknowledgedKeyWarning) {
                            StitchPill(
                                text = "Secured",
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            StitchPill(
                                text = "Needs Backup",
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(MimoraSpacing.small))

                    Text(
                        text = "Your 24-word private key encrypts all your diary entries and Google Drive backups. It is stored ONLY on your device. If lost, your backups cannot be decrypted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(MimoraSpacing.medium))

                    Button(
                        onClick = { viewModel.startDisclaimerCountdown() },
                        shape = MimoraShapes.full,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_security),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (prefs.hasAcknowledgedKeyWarning) "View & Copy Master Private Key"
                            else "View & Save Master Private Key",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(MimoraSpacing.large))
        }

        // --- GOOGLE DRIVE & CLOUD BACKUP CARD ---
        item {
            SettingsSectionHeader(title = "Google Drive & Cloud Backup")

            Card(
                shape = MimoraShapes.card,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(MimoraSpacing.large)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_google_drive),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Google Drive Backup",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val lastBackupStr = if (prefs.lastBackupTimestamp > 0L) {
                                val instant = Instant.ofEpochMilli(prefs.lastBackupTimestamp)
                                val ldt = instant.atZone(ZoneId.systemDefault()).toLocalDateTime()
                                val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy · HH:mm")
                                "Last synced: ${ldt.format(formatter)}"
                            } else {
                                "Never backed up"
                            }
                            Text(
                                text = lastBackupStr,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(MimoraSpacing.small))

                    // Google Sign-In + private Drive app data folder
                    Surface(
                        shape = MimoraShapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_google_drive),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Google Drive Cloud Sync",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                DriveStatusIndicator(connected = prefs.googleDriveConnected)
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when {
                                    prefs.googleDriveConnected && prefs.googleAccountEmail.isNotBlank() ->
                                        "Account: ${prefs.googleAccountEmail}"
                                    prefs.googleDriveConnected ->
                                        "Connected. Backups are stored in MIMORA's private Drive folder."
                                    else ->
                                        "Sign in with Google to sync encrypted backups automatically. MIMORA can only see its own private folder."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            if (!prefs.googleDriveConnected) {
                                Button(
                                    onClick = {
                                        onDriveAction(DriveAction.Connect)
                                    },
                                    shape = MimoraShapes.full,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !uiState.isProcessingBackup
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_google_drive),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Sign in with Google")
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            onDriveAction(DriveAction.Sync)
                                        },
                                        shape = MimoraShapes.full,
                                        modifier = Modifier.weight(1f),
                                        enabled = !uiState.isProcessingBackup
                                    ) {
                                        Text("Sync now")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            onDriveAction(DriveAction.Restore)
                                        },
                                        shape = MimoraShapes.full,
                                        modifier = Modifier.weight(1f),
                                        enabled = !uiState.isProcessingBackup
                                    ) {
                                        Text("Restore")
                                    }
                                }

                                TextButton(
                                    onClick = { viewModel.disconnectGoogleDrive() },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text(
                                        text = "Disconnect",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(MimoraSpacing.small))

                    Text(
                        text = "Prefer to manage the file yourself? Export the same encrypted .mimora archive to any location through Android's file picker, or import one back.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(MimoraSpacing.medium))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.prepareEncryptedBackupPayload { bytes ->
                                    pendingExportBytes = bytes
                                    val fileName = "mimora_backup_${DateRules.formatDate(LocalDate.now())}.mimora"
                                    createDocumentLauncher.launch(fileName)
                                }
                            },
                            shape = MimoraShapes.full,
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isProcessingBackup
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_cloud_backup),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export file")
                        }

                        OutlinedButton(
                            onClick = {
                                openDocumentLauncher.launch(arrayOf("*/*", "application/octet-stream"))
                            },
                            shape = MimoraShapes.full,
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isProcessingBackup
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_restore),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import file")
                        }
                    }

                    Spacer(modifier = Modifier.height(MimoraSpacing.medium))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Daily Auto-Backup",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Prompt to update cloud backup upon daily entries",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = prefs.autoBackupEnabled,
                            onCheckedChange = { viewModel.toggleAutoBackup(it) }
                        )
                    }

                    // Backup/restore results are shown in a root-level snackbar so
                    // they are also visible when an action starts from a top bar.
                }
            }
            Spacer(modifier = Modifier.height(MimoraSpacing.large))
        }

        item {
            ExtrasSettingsCard(
                prefs = prefs,
                isCheckingUpdate = uiState.isCheckingUpdate,
                onToggleReminder = { viewModel.toggleWritingReminder(it) },
                onCheckUpdate = { viewModel.checkForUpdates() },
                onShowNote = { showDeveloperNote = true }
            )
        }

        // --- SESSION / LOGOUT ---
        item {
            Card(
                shape = MimoraShapes.card,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(MimoraSpacing.large)) {
                    Text(
                        text = "Session",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(MimoraSpacing.small))
                    Text(
                        text = "Return to the welcome screen to create or restore a diary. Your entries stay on this device and Google Drive remains connected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(MimoraSpacing.normal))
                    OutlinedButton(
                        onClick = { showLogoutConfirm = true },
                        shape = MimoraShapes.full,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_logout),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Log out", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(MimoraSpacing.large))
        }

        // --- STITCH FOOTER ---
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = MimoraSpacing.normal),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Crafted for mindful reflection",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "End-to-End Encrypted (AES-256-GCM) · Offline First",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(MimoraSpacing.doubleLarge))
        }
    }

    // --- MANDATORY 10-SECOND DISCLAIMER DIALOG ---
    if (uiState.showDisclaimerModal) {
        KeySecurityDisclaimerDialog(
            remainingSeconds = uiState.disclaimerRemainingSeconds,
            canProceed = uiState.canProceedDisclaimer,
            onConfirm = { viewModel.acknowledgeDisclaimerAndRevealKey() },
            onDismiss = { viewModel.dismissDisclaimer() }
        )
    }

    // --- 24-WORD MASTER PRIVATE KEY DISPLAY & COPY DIALOG ---
    if (uiState.showPrivateKeyModal) {
        MasterPrivateKeyDialog(
            words = uiState.recoveryPhraseWords,
            hasCopied = uiState.hasCopiedPrivateKey,
            hasConfirmedSaved = uiState.hasConfirmedSavedOffline,
            onCopyKey = { viewModel.copyPrivateKey(context) },
            onToggleConfirmed = { viewModel.setConfirmedSavedOffline(it) },
            onRegenerate = { viewModel.regeneratePrivateKey() },
            onDismiss = { viewModel.closePrivateKeyModal() }
        )
    }

    // --- RESTORE BACKUP DIALOG ---
    if (uiState.showRestoreDialog) {
        RestoreBackupDialog(
            isProcessing = uiState.isProcessingBackup,
            onConfirmRestore = { phrase -> viewModel.executeRestore(phrase) },
            onDismiss = { viewModel.closeRestoreDialog() }
        )
    }

    uiState.updateResult?.let { result ->
        UpdateResultDialog(
            result = result,
            onDismiss = { viewModel.dismissUpdateResult() },
            onOpenReleases = { openLink(context, REPO_URL + "/releases") }
        )
    }

    if (showDeveloperNote) {
        DeveloperNoteDialog(onDismiss = { showDeveloperNote = false })
    }

    // --- LOGOUT CONFIRMATION ---
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_logout),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            },
            title = {
                Text(
                    text = "Log out of MIMORA?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = buildString {
                        append("You will return to the welcome screen to create or restore a diary. ")
                        append("Your entries stay on this device and Google Drive stays connected. ")
                        if (prefs.appLockEnabled && prefs.pinHash.isNotBlank()) {
                            append("You'll be asked for your PIN or fingerprint to confirm.")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirm = false
                        onLogout()
                    },
                    shape = MimoraShapes.full,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Log out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- PIN SETUP DIALOG ---
    if (showPinDialog) {
        PinSetupDialog(
            biometricAvailable = fingerprintAvailable,
            onDismiss = { showPinDialog = false },
            onConfirm = { pin, useBiometric ->
                viewModel.toggleAppLock(
                    enabled = true,
                    pin = pin,
                    biometricEnabled = useBiometric
                )
                showPinDialog = false
            }
        )
    }

}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = MimoraSpacing.small, start = 4.dp)
    )
}

/**
 * Mandatory 10-Second Security Warning Page.
 * Stays active for at least 10 seconds before the user can proceed,
 * ensuring the user understands the critical nature of the private master key.
 */
@Composable
internal fun KeySecurityDisclaimerDialog(
    remainingSeconds: Int,
    canProceed: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            // Dismiss permitted only after user can proceed, or via cancel button
            if (canProceed) onDismiss()
        },
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MimoraShapes.full)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_security),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Text(
                text = "Critical Security Disclaimer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "MIMORA utilizes zero-knowledge AES-256-GCM encryption. Before viewing your private master key, please read the following terms carefully:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = MimoraShapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SecurityPoint(
                            number = "1",
                            text = "Your 24-word private key is your ONLY way to decrypt and restore Google Drive backups on any device."
                        )
                        SecurityPoint(
                            number = "2",
                            text = "MIMORA has NO servers and NO recovery service. If you lose your key, your backups are permanently lost."
                        )
                        SecurityPoint(
                            number = "3",
                            text = "You must copy and write down this key in a secure physical location or password manager."
                        )
                        SecurityPoint(
                            number = "4",
                            text = "Never share your private key with anyone."
                        )
                    }
                }

                // 10-Second Progress Indicator
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val progress = (10 - remainingSeconds).coerceIn(0, 10) / 10f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(MimoraShapes.full),
                        color = if (canProceed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (canProceed) "✓ Mandatory reading complete"
                            else "Mandatory reading period:",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (canProceed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (canProceed) "Ready" else "${remainingSeconds}s remaining",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (canProceed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = canProceed,
                shape = MimoraShapes.full,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (canProceed) "I Understand & Reveal Private Key"
                    else "Please Read Carefully (${remainingSeconds}s)"
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SecurityPoint(number: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(MimoraShapes.full)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = 11.sp
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 24-Word Master Private Key Display Dialog with Copy button and offline save confirmation.
 */
@Composable
internal fun MasterPrivateKeyDialog(
    words: List<String>,
    hasCopied: Boolean,
    hasConfirmedSaved: Boolean,
    onCopyKey: () -> Unit,
    onToggleConfirmed: (Boolean) -> Unit,
    onRegenerate: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "Done & Sealed",
    onCancel: (() -> Unit)? = null
) {
    var showRegenConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (onCancel != null) onCancel() else onDismiss() },
        title = {
            Column {
                Text(
                    text = "Vault Master Private Key",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "24-Word Offline Secret Phrase (BIP-39)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Copy these 24 words and save them in a safe place. You will need them to decrypt your Google Drive backups.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 24 Words Grid (3 Columns)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.height(260.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(words) { index, word ->
                        Surface(
                            shape = MimoraShapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${index + 1}.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = word,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // Copy to Clipboard Button
                Button(
                    onClick = onCopyKey,
                    shape = MimoraShapes.full,
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (hasCopied) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_copy),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (hasCopied) "✓ Private Key Copied to Clipboard!"
                        else "Copy 24-Word Private Key",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Confirmation Checkbox
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleConfirmed(!hasConfirmedSaved) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = hasConfirmedSaved,
                        onCheckedChange = { onToggleConfirmed(it) }
                    )
                    Text(
                        text = "I have copied and saved my 24-word private key in a safe offline location.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = MimoraShapes.full,
                enabled = hasCopied && hasConfirmedSaved,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(
                    onClick = { showRegenConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Generate New Master Key", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (onCancel != null) {
                    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text("Back to options", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    )

    if (showRegenConfirm) {
        AlertDialog(
            onDismissRequest = { showRegenConfirm = false },
            title = { Text("Generate New Master Key?") },
            text = {
                Text("Generating a new master key means existing Google Drive backups encrypted with your current key cannot be decrypted without the old phrase. Proceed?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRegenConfirm = false
                        onRegenerate()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Regenerate Key")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegenConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Dialog to input 24 words and decrypt an archive from Google Drive.
 */
@Composable
private fun RestoreBackupDialog(
    isProcessing: Boolean,
    onConfirmRestore: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Always blank: a restore key must come from the user, never from this
    // device's stored phrase (the archive may belong to another device).
    var phraseInput by remember { mutableStateOf("") }
    val wordCount = phraseInput.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }.size
    val isValidCount = wordCount == 24

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Decrypt Google Drive Backup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Enter your 24-word private key to decrypt archive",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "The selected backup is protected with AES-256-GCM authenticated encryption. Paste or enter your 24-word phrase below:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = phraseInput,
                    onValueChange = { phraseInput = it },
                    label = { Text("24-Word Private Key") },
                    placeholder = { Text("e.g. abandon ability able about...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    shape = MimoraShapes.medium,
                    maxLines = 5
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$wordCount / 24 words entered",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isValidCount) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    if (isValidCount) {
                        Text(
                            text = "✓ Valid Word Count",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmRestore(phraseInput) },
                enabled = isValidCount && !isProcessing,
                shape = MimoraShapes.full
            ) {
                Text(if (isProcessing) "Decrypting..." else "Decrypt & Restore")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isProcessing) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PinSetupDialog(
    biometricAvailable: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, Boolean) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var useBiometric by remember { mutableStateOf(biometricAvailable) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Security PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Use 4-6 digits. This PIN also protects every delete action.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = it.filter { char -> char.isDigit() }.take(6)
                        errorMessage = null
                    },
                    label = { Text("Enter PIN") },
                    singleLine = true,
                    shape = MimoraShapes.medium,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = {
                        confirmPin = it.filter { char -> char.isDigit() }.take(6)
                        errorMessage = null
                    },
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    shape = MimoraShapes.medium,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (biometricAvailable) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = useBiometric,
                            onCheckedChange = { useBiometric = it }
                        )
                        Text(
                            text = "Also unlock with fingerprint",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pin.length < 4) {
                        errorMessage = "PIN must be at least 4 digits"
                    } else if (pin != confirmPin) {
                        errorMessage = "PINs do not match"
                    } else {
                        onConfirm(pin, useBiometric && biometricAvailable)
                    }
                },
                shape = MimoraShapes.full
            ) {
                Text("Enable PIN Lock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun GoogleDriveRestoreDialog(
    files: List<GoogleDriveFile>,
    isLoading: Boolean,
    isProcessing: Boolean,
    onRestore: (fileId: String, phrase: String) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFileId by remember { mutableStateOf(files.firstOrNull()?.id) }
    // Never pre-fill with the local key; the user must supply the backup's key.
    var phrase by remember { mutableStateOf("") }
    val wordCount = phrase.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }.size
    val hasValidKey = wordCount == 24

    LaunchedEffect(files) {
        if (selectedFileId == null || files.none { it.id == selectedFileId }) {
            selectedFileId = files.firstOrNull()?.id
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_restore),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Restore from Google Drive",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Choose an encrypted backup. You need your 24-word private key to open it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else if (files.isEmpty()) {
                    Text(
                        text = "No backups found in this Google account yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    files.forEach { file ->
                        Surface(
                            shape = MimoraShapes.medium,
                            color = if (file.id == selectedFileId) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedFileId = file.id }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RadioButton(
                                    selected = file.id == selectedFileId,
                                    onClick = { selectedFileId = file.id }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${formatBackupSize(file.sizeBytes)} · ${formatDriveTime(file.modifiedTime)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = phrase,
                    onValueChange = { phrase = it },
                    label = { Text("24-word private key") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    shape = MimoraShapes.medium
                )

                Text(
                    text = if (hasValidKey) {
                        "24 / 24 words · key required, Google cannot decrypt this backup."
                    } else {
                        "$wordCount / 24 words · a backup cannot be restored without the full key."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasValidKey) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )

                TextButton(onClick = onRefresh, enabled = !isLoading) {
                    Text("Refresh list")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedFileId?.let { onRestore(it, phrase) } },
                enabled = selectedFileId != null && hasValidKey && !isProcessing && !isLoading,
                shape = MimoraShapes.full
            ) {
                Text(if (isProcessing) "Restoring..." else "Restore")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatDriveTime(raw: String): String = try {
    Instant.parse(raw).atZone(ZoneId.systemDefault()).toLocalDateTime()
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
} catch (_: Exception) {
    raw.take(16).replace('T', ' ')
}

private fun formatBackupSize(bytes: Long): String = when {
    bytes <= 0L -> "unknown size"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    else -> "${bytes / (1024L * 1024L)} MB"
}
