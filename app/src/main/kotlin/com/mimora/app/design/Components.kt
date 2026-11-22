package com.mimora.app.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mimora.app.R

enum class SaveState {
    IDLE,
    SAVING,
    SAVED,
    ERROR
}

/**
 * Autosave indicator pill matching Stitch "Serene Sanctuary" design:
 * An emerald green dot with "Saved" or subtle spinning indicator for "Saving...".
 */
@Composable
fun SaveStateIndicator(
    state: SaveState,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state != SaveState.IDLE,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(MimoraShapes.full)
                .background(
                    when (state) {
                        SaveState.SAVING -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                        SaveState.SAVED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        SaveState.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                        SaveState.IDLE -> Color.Transparent
                    }
                )
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            when (state) {
                SaveState.SAVING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(10.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Saving...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
                SaveState.SAVED -> {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981)) // Emerald green pulse
                    )
                    Text(
                        text = "Saved",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
                SaveState.ERROR -> {
                    Text(
                        text = "Save error",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
                SaveState.IDLE -> Unit
            }
        }
    }
}

/**
 * Accessible Icon Button ensuring minimum 48dp touch target size.
 */
@Composable
fun MimoraIconButton(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = MimoraTouchTargets.minTarget, minHeight = MimoraTouchTargets.minTarget)
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Stitch Pill Tag component for moods, filters, and metadata.
 */
@Composable
fun StitchPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(MimoraShapes.full)
            .background(containerColor)
            .then(
                if (onClick != null) Modifier.clickable { onClick() } else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        icon?.invoke()
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = contentColor
        )
    }
}

/**
 * Audio waveform visualizer simulating audio frequencies.
 */
@Composable
fun WaveformVisualizer(
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    barCount: Int = 18,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.outlineVariant
) {
    val pattern = remember { listOf(4, 8, 14, 20, 16, 10, 18, 24, 20, 12, 16, 8, 14, 22, 12, 6, 10, 4) }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val barHeight = pattern[i % pattern.size].dp
            val isPlayed = i < (if (isPlaying) 10 else 5)
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(barHeight)
                    .clip(MimoraShapes.full)
                    .background(if (isPlayed) activeColor else inactiveColor)
            )
        }
    }
}

/**
 * 5 Daily Moods with modern SVG vector icons and calibrated canvas wash colors.
 * Used for the Home screen mood selector and dynamic card parchment tinting.
 */
enum class DailyMood(
    val id: String,
    val title: String,
    val iconRes: Int,
    val canvasLightColor: Color,
    val canvasDarkColor: Color,
    val accentColor: Color
) {
    CALM(
        id = "calm",
        title = "Calm",
        iconRes = R.drawable.ic_mood_calm,
        canvasLightColor = Color(0xFFFAF7FD), // Soft tranquil lavender tint
        canvasDarkColor = Color(0xFF1E1A25),
        accentColor = Color(0xFF7650D8)
    ),
    RADIANT(
        id = "radiant",
        title = "Radiant",
        iconRes = R.drawable.ic_mood_radiant,
        canvasLightColor = Color(0xFFFDFBF5), // Warm subtle amber glow
        canvasDarkColor = Color(0xFF221F17),
        accentColor = Color(0xFFC78926)
    ),
    REFLECTIVE(
        id = "reflective",
        title = "Reflective",
        iconRes = R.drawable.ic_mood_reflective,
        canvasLightColor = Color(0xFFF6F8FB), // Quiet twilight indigo mist
        canvasDarkColor = Color(0xFF171A22),
        accentColor = Color(0xFF536DFE)
    ),
    GRATEFUL(
        id = "grateful",
        title = "Grateful",
        iconRes = R.drawable.ic_mood_grateful,
        canvasLightColor = Color(0xFFFDF7F9), // Gentle blossom rose tint
        canvasDarkColor = Color(0xFF24181E),
        accentColor = Color(0xFFD85D8A)
    ),
    PEACEFUL(
        id = "peaceful",
        title = "Peaceful",
        iconRes = R.drawable.ic_mood_peaceful,
        canvasLightColor = Color(0xFFF6FAF7), // Fresh mint sage tint
        canvasDarkColor = Color(0xFF161E18),
        accentColor = Color(0xFF43A047)
    );

    companion object {
        fun fromId(id: String?): DailyMood = entries.firstOrNull { it.id == id } ?: CALM
    }
}

/**
 * Modern Segmented Control with smooth switch animation.
 * Features a unified continuous pill container with zero borders/shadows and
 * smooth animated transitions between active items.
 */
@Composable
fun <T> StitchSegmentedControl(
    items: List<T>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    itemLabel: (T) -> String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MimoraShapes.full,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = selectedIndex == index
                val animatedBgColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    animationSpec = tween(durationMillis = 240),
                    label = "segmentBg"
                )
                val animatedTextColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(durationMillis = 240),
                    label = "segmentText"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(MimoraShapes.full)
                        .background(animatedBgColor)
                        .clickable { onItemSelected(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = itemLabel(item),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = animatedTextColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Modern Stitch Pill with Vector Drawable Icon and optional click handler.
 */
@Composable
fun StitchPillWithIcon(
    iconRes: Int,
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable { onClick() }
    } else Modifier

    Box(
        modifier = modifier
            .clip(MimoraShapes.full)
            .background(containerColor)
            .then(clickableModifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
        }
    }
}
