package com.mimora.app.feature.onboarding

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mimora.app.R
import com.mimora.app.design.MimoraShapes
import com.mimora.app.design.MimoraSpacing
import com.mimora.app.design.StitchPill
import com.mimora.app.feature.backup.DriveAction
import com.mimora.app.feature.backup.rememberDriveAuthorizer
import com.mimora.app.feature.settings.SettingsViewModel

@Composable
fun OnboardingScreen(
    viewModel: SettingsViewModel,
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Drive backups live in appDataFolder, which the system file picker cannot
    // browse. First-run restore must use the same signed-in Drive flow as Settings.
    val onDriveAction = rememberDriveAuthorizer(viewModel)
    var showRestoreKeyDialog by remember { mutableStateOf(false) }
    var pickedBackupBytes by remember { mutableStateOf<ByteArray?>(null) }
    var restoreErrorMessage by remember { mutableStateOf<String?>(null) }

    // SAF File Picker for picking existing backup from Google Drive or local storage
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { fileUri ->
            try {
                val bytes = context.contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    pickedBackupBytes = bytes
                    // The ViewModel owns the archive that executeRestore() reads.
                    // Without this, onboarding shows the key dialog but the
                    // restore fails with "No backup archive is loaded".
                    viewModel.setPendingRestoreBytes(bytes)
                    showRestoreKeyDialog = true
                    restoreErrorMessage = null
                } else {
                    restoreErrorMessage = "Selected backup file is empty."
                }
            } catch (e: Exception) {
                restoreErrorMessage = "Failed to load backup: ${e.message}"
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MimoraSpacing.large),
        contentPadding = PaddingValues(vertical = MimoraSpacing.doubleLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // --- HERO BRANDING ---
        item {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(MimoraShapes.large)
                    // Fixed brand colours: app identity never follows a writing theme.
                    .background(androidx.compose.ui.graphics.Color(0xFF6750A4)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_diary),
                    contentDescription = "MIMORA Logo",
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(MimoraSpacing.medium))

            Text(
                text = "Welcome to MIMORA",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "A serene, private sanctuary for your daily thoughts and memories.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = MimoraSpacing.medium)
            )

            Spacer(modifier = Modifier.height(MimoraSpacing.small))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StitchPill(
                    text = "Offline First",
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StitchPill(
                    text = "AES-256-GCM Encrypted",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(MimoraSpacing.doubleLarge))
        }

        // --- OPTION A: FRESH JOURNAL ---
        item {
            Card(
                shape = MimoraShapes.card,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(MimoraSpacing.large)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(MimoraShapes.full)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_edit),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Start a Fresh Journal",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Generate a new vault key and begin writing today",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(MimoraSpacing.medium))

                    Button(
                        onClick = {
                            // Every fresh install mints its own unique key here.
                            viewModel.createFreshJournal(onOnboardingComplete)
                        },
                        shape = MimoraShapes.full,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Create New Journal", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(MimoraSpacing.large))
        }

        // --- OPTION B: RESTORE FROM GOOGLE DRIVE / REINSTALL ---
        item {
            Card(
                shape = MimoraShapes.card,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(MimoraSpacing.large)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(MimoraShapes.full)
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_cloud_backup),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Restore Previous Journal",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "Reinstalling? Restore all diary entries, photos, and voice notes from your Google Drive backup using your 24-word private key.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(MimoraSpacing.medium))

                    Button(
                        onClick = { onDriveAction(DriveAction.Restore) },
                        shape = MimoraShapes.full,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_cloud_backup),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restore from Google Drive", fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            openDocumentLauncher.launch(arrayOf("*/*", "application/octet-stream"))
                        },
                        shape = MimoraShapes.full,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_restore),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choose backup file instead", fontWeight = FontWeight.SemiBold)
                    }

                    if (restoreErrorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = restoreErrorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(MimoraSpacing.large))
        }

        // --- FOOTER PRIVACY NOTE ---
        item {
            Text(
                text = "MIMORA has no central servers. Your journal is 100% offline and decrypted only with your private key.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }

    // --- RESTORE KEY & RESTORATION DIALOG ---
    if (showRestoreKeyDialog && pickedBackupBytes != null) {
        var phraseInput by remember { mutableStateOf("") }
        var isDecrypting by remember { mutableStateOf(false) }
        var decryptError by remember { mutableStateOf<String?>(null) }
        val words = phraseInput.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        val isValidWordCount = words.size == 24

        AlertDialog(
            onDismissRequest = { if (!isDecrypting) showRestoreKeyDialog = false },
            title = {
                Text(
                    text = "Decrypt & Restore Journal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Backup archive loaded (${pickedBackupBytes?.size} bytes). Enter your 24-word master private key to decrypt and restore all memories:",
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
                            .height(130.dp),
                        shape = MimoraShapes.medium,
                        maxLines = 5
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${words.size} / 24 words entered",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isValidWordCount) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        if (isValidWordCount) {
                            Text(
                                text = "✓ Valid Word Count",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (decryptError != null) {
                        Text(
                            text = decryptError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pickedBackupBytes == null) return@Button
                        isDecrypting = true
                        decryptError = null
                        viewModel.executeRestore(phraseInput) { result ->
                            isDecrypting = false
                            if (result.isSuccess) {
                                // Only a verified decryption may finish onboarding.
                                viewModel.setCompletedOnboarding()
                                onOnboardingComplete()
                            } else {
                                decryptError = SettingsViewModel.describeRestoreFailure(
                                    result.exceptionOrNull() ?: IllegalStateException("Unknown restore error")
                                )
                            }
                        }
                    },
                    enabled = isValidWordCount && !isDecrypting,
                    shape = MimoraShapes.full
                ) {
                    Text(if (isDecrypting) "Decrypting..." else "Decrypt & Restore Journal")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRestoreKeyDialog = false },
                    enabled = !isDecrypting
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
