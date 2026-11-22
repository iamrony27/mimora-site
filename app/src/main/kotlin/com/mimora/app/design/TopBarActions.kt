package com.mimora.app.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mimora.app.R
import com.mimora.app.feature.backup.SyncOperation

/**
 * Backup and restore shortcuts for the top bar. A running sync shows a sweeping
 * progress ring, a soft breathing halo, and an accent-tinted icon so the state
 * is readable at a glance instead of relying on a generic spinner.
 */
@Composable
fun BackupRestoreTopBarActions(
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    isSyncing: Boolean = false,
    syncOperation: SyncOperation = SyncOperation.IDLE,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedTopBarIcon(
            iconRes = R.drawable.ic_cloud_backup,
            description = "Back up diary to Google Drive",
            isActive = isSyncing && syncOperation != SyncOperation.RESTORE,
            reverseSpin = false,
            onClick = onBackup
        )
        AnimatedTopBarIcon(
            iconRes = R.drawable.ic_restore,
            description = "Restore diary from Google Drive",
            isActive = isSyncing && syncOperation == SyncOperation.RESTORE,
            reverseSpin = true,
            onClick = onRestore
        )
    }
}

@Composable
private fun AnimatedTopBarIcon(
    iconRes: Int,
    description: String,
    isActive: Boolean,
    reverseSpin: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
        label = "topBarIconScale"
    )
    val containerColor by animateColorAsState(
        targetValue = if (pressed || isActive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 180),
        label = "topBarIconBackground"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "topBarSync")
    val spin by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "topBarSyncSpin"
    )
    val halo by infiniteTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "topBarSyncHalo"
    )

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(MimoraShapes.full)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = isActive,
            enter = fadeIn() + scaleIn(initialScale = 0.7f),
            exit = fadeOut() + scaleOut(targetScale = 0.7f)
        ) {
            Box(
                modifier = Modifier.size(34.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .graphicsLayer {
                            scaleX = halo
                            scaleY = halo
                        }
                        .clip(MimoraShapes.full)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                )
                CircularProgressIndicator(
                    progress = { 0.72f },
                    modifier = Modifier
                        .size(34.dp)
                        .graphicsLayer {
                            rotationZ = if (reverseSpin) -spin else spin
                        },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent,
                    strokeWidth = 2.dp
                )
            }
        }

        IconButton(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = description,
                tint = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
            )
        }
    }
}
