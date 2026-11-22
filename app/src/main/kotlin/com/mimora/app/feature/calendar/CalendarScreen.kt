package com.mimora.app.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mimora.app.R
import com.mimora.app.core.model.DateRules
import com.mimora.app.core.model.DiaryEntry
import com.mimora.app.design.MimoraIconButton
import com.mimora.app.design.MimoraShapes
import com.mimora.app.design.MimoraSpacing
import com.mimora.app.design.StitchPill
import com.mimora.app.design.StitchSegmentedControl
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onDateClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    topBarActions: @Composable () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }
    val monthTitle = remember(uiState.selectedYearMonth) { uiState.selectedYearMonth.format(monthFormatter) }

    var selectedFilter by remember { mutableStateOf("All") }

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
                            painter = painterResource(R.drawable.ic_calendar),
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
                            text = "Calendar",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                topBarActions()
            }

            // Filter segmented switch control
            val filterOptions = listOf(
                "All" to "All (${uiState.entriesInMonth.size})",
                "Photos" to "Photos",
                "Voice" to "Voice Notes"
            )
            val currentFilterIndex = filterOptions.indexOfFirst { it.first == selectedFilter }.coerceAtLeast(0)
            StitchSegmentedControl(
                items = filterOptions,
                selectedIndex = currentFilterIndex,
                onItemSelected = { selectedFilter = filterOptions[it].first },
                itemLabel = { it.second },
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Spacer(modifier = Modifier.height(MimoraSpacing.small))
        }

        // --- STITCH CALENDAR CARD CONTAINER ---
        item {
            Card(
                shape = MimoraShapes.card,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(MimoraSpacing.large)) {
                    // Month title & arrows
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = monthTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row {
                            MimoraIconButton(
                                painter = painterResource(R.drawable.ic_calendar),
                                contentDescription = "Previous Month",
                                onClick = { viewModel.previousMonth() }
                            )
                            MimoraIconButton(
                                painter = painterResource(R.drawable.ic_edit),
                                contentDescription = "Next Month",
                                onClick = { viewModel.nextMonth() }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(MimoraSpacing.small))

                    // Days of week header (Sun Mon Tue Wed Thu Fri Sat)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        val daysOfWeek = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                        for (d in daysOfWeek) {
                            Text(
                                text = d,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Month Grid
                    Box(modifier = Modifier.height(260.dp)) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(7),
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = false
                        ) {
                            items(uiState.daysInGrid) { dayIndicator ->
                                CalendarDayCell(
                                    indicator = dayIndicator,
                                    onClick = {
                                        if (!dayIndicator.isFuture) {
                                            onDateClick(dayIndicator.date)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Legend Pill (Stitch Design)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MimoraShapes.full)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LegendItem(color = MaterialTheme.colorScheme.primary, label = "Journal")
                            LegendItem(color = MaterialTheme.colorScheme.secondary, label = "Photo")
                            LegendItem(color = MaterialTheme.colorScheme.tertiary, label = "Voice Memo")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(MimoraSpacing.large))
        }

        // --- STITCH "ON THIS DAY · 1 YEAR AGO" MEMORY CARD ---
        if (uiState.memoriesOnThisDay.isNotEmpty()) {
            item {
                for (memory in uiState.memoriesOnThisDay) {
                    val yearsAgo = LocalDate.now().year - memory.date.year
                    Card(
                        shape = MimoraShapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDateClick(memory.date) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(MimoraSpacing.normal),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "✨", fontSize = 16.sp)
                                }
                                Column {
                                    Text(
                                        text = "On This Day · $yearsAgo ${if (yearsAgo == 1) "Year" else "Years"} Ago",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = memory.title.ifBlank { memory.content.take(40) + "..." },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Text(
                                text = "→",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(MimoraSpacing.medium))
                }
            }
        }

        // --- MONTH ENTRIES SECTION HEADER ---
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = MimoraSpacing.small),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Entries in ${uiState.selectedYearMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }} (${uiState.entriesInMonth.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Sort by Date",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // --- MONTH ENTRIES CARDS ---
        if (uiState.entriesInMonth.isEmpty()) {
            item {
                Text(
                    text = "No diary entries recorded for this month.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = MimoraSpacing.small)
                )
            }
        } else {
            items(uiState.entriesInMonth, key = { it.id }) { entry ->
                StitchEntryCard(
                    entry = entry,
                    onClick = { onDateClick(entry.date) }
                )
                Spacer(modifier = Modifier.height(MimoraSpacing.small))
            }
        }

        item {
            Spacer(modifier = Modifier.height(MimoraSpacing.doubleLarge))
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CalendarDayCell(
    indicator: DayIndicator,
    onClick: () -> Unit
) {
    val isSelectable = !indicator.isFuture && indicator.isCurrentMonth
    val textColor = when {
        indicator.isToday -> MaterialTheme.colorScheme.primary
        indicator.isFuture -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
        !indicator.isCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(
                if (indicator.isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                else Color.Transparent
            )
            .clickable(enabled = isSelectable, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${indicator.dayNumber}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (indicator.isToday || indicator.hasEntry) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )

            // Indicators row for Text, Photo, Voice (Stitch Dots)
            if (indicator.hasEntry) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    if (indicator.hasText) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                    if (indicator.hasPhoto) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary)
                        )
                    }
                    if (indicator.hasVoice) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Stitch Entry Preview Card in Calendar view.
 */
@Composable
private fun StitchEntryCard(
    entry: DiaryEntry,
    onClick: () -> Unit
) {
    Card(
        shape = MimoraShapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(MimoraSpacing.medium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.date.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "3 min read",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = entry.title.ifBlank { "Journal Entry" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = entry.content.ifBlank { "No text content recorded" },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (entry.hasPhoto || entry.hasVoice) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (entry.hasPhoto) {
                        StitchPill(
                            text = "Photo",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    if (entry.hasVoice) {
                        StitchPill(
                            text = "Audio Thought",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
    }
}
