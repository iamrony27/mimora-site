package com.mimora.app.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mimora.app.BuildConfig
import com.mimora.app.R
import com.mimora.app.data.preferences.UserPreferences
import com.mimora.app.design.MimoraShapes
import com.mimora.app.design.MimoraSpacing
import com.mimora.app.feature.updates.UpdateResult

internal const val UPI_ID = "7074018142@ptsbi"
internal const val REPO_URL = "https://github.com/iamrony27/mimora-site"
internal const val ISSUE_URL = "$REPO_URL/issues/new"

internal fun openLink(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

internal fun openUpiApp(context: Context) {
    val url = "upi://pay?pa=${Uri.encode(UPI_ID)}&pn=MIMORA&cu=INR&tn=Support%20MIMORA"
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        Toast.makeText(context, "No UPI app is installed.", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun ExtrasSettingsCard(
    prefs: UserPreferences,
    isCheckingUpdate: Boolean,
    onToggleReminder: (Boolean) -> Unit,
    onCheckUpdate: () -> Unit,
    onShowNote: () -> Unit
) {
    val context = LocalContext.current
    var showDonate by remember { mutableStateOf(false) }

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
                text = "MIMORA & You",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Small ways to keep your journal going.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(MimoraSpacing.medium))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_reminder),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Evening writing reminder",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "A gentle nudge every day at 7:00 PM",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = prefs.writingReminderEnabled,
                    onCheckedChange = onToggleReminder
                )
            }

            Spacer(modifier = Modifier.height(MimoraSpacing.small))
            ExtraActionRow(
                iconRes = R.drawable.ic_cloud_backup,
                title = "Check for updates",
                subtitle = "Current version ${BuildConfig.VERSION_NAME}",
                trailing = if (isCheckingUpdate) "Checking…" else null,
                onClick = onCheckUpdate
            )
            ExtraActionRow(
                iconRes = R.drawable.ic_sparkle,
                title = "Star MIMORA on GitHub",
                subtitle = "Help other journalers find it",
                onClick = { openLink(context, REPO_URL) }
            )
            ExtraActionRow(
                iconRes = R.drawable.ic_edit,
                title = "Report an issue",
                subtitle = "Bug, idea, or something confusing",
                onClick = { openLink(context, ISSUE_URL) }
            )
            ExtraActionRow(
                iconRes = R.drawable.ic_favorite,
                title = "Support the developer",
                subtitle = "UPI donation · $UPI_ID",
                onClick = { showDonate = true }
            )
            ExtraActionRow(
                iconRes = R.drawable.ic_diary,
                title = "A note from the developer",
                subtitle = "Why MIMORA exists",
                onClick = onShowNote
            )
        }
    }

    if (showDonate) {
        DonateDialog(
            onPay = { openUpiApp(context) },
            onCopy = {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText("MIMORA UPI ID", UPI_ID))
                Toast.makeText(context, "UPI ID copied", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showDonate = false }
        )
    }
}

@Composable
private fun ExtraActionRow(
    iconRes: Int,
    title: String,
    subtitle: String,
    trailing: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (trailing != null) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            TextButton(onClick = onClick) { Text("Open") }
        }
    }
}

@Composable
internal fun UpdateResultDialog(
    result: UpdateResult,
    onDismiss: () -> Unit,
    onOpenReleases: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (result) {
                    is UpdateResult.Available -> "Update available — v${result.info.version}"
                    is UpdateResult.UpToDate -> "You're up to date"
                    is UpdateResult.Error -> "Update check"
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            when (result) {
                is UpdateResult.Available -> Column {
                    Text("A newer MIMORA build is ready to download.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        result.info.releaseNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is UpdateResult.UpToDate -> Text(
                    "MIMORA v${BuildConfig.VERSION_NAME} is the latest release."
                )

                is UpdateResult.Error -> Text(result.message)
            }
        },
        confirmButton = {
            if (result is UpdateResult.Available) {
                TextButton(onClick = { onOpenReleases(); onDismiss() }) { Text("Download") }
            } else {
                TextButton(onClick = onDismiss) { Text("OK") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
internal fun StarPromptDialog(onStar: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Like MIMORA?", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "You've been writing with MIMORA for a few days. If this quiet space has helped you, " +
                    "a star on GitHub is a kind, free way to say thanks. This message won't appear again."
            )
        },
        confirmButton = {
            TextButton(onClick = { onStar(); onDismiss() }) { Text("Star on GitHub") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Maybe later") }
        }
    )
}

@Composable
private fun DonateDialog(onPay: () -> Unit, onCopy: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Support MIMORA", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("If MIMORA has been useful, you can support its development through UPI.")
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    UPI_ID,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPay(); onDismiss() }) { Text("Pay with UPI") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCopy) { Text("Copy ID") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

@Composable
internal fun DeveloperNoteDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("A note from the developer", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "I built MIMORA because some thoughts deserve a place that belongs only to you. " +
                        "No ads, no tracking, no company reading your words — just you and your diary.",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Every update you see here is made in the quiet hours, with care. " +
                        "If MIMORA has helped you slow down and understand yourself a little better, " +
                        "that is the whole reason it exists.",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Thank you for trusting it with your memories.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "— The MIMORA developer",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Thank you") } }
    )
}
