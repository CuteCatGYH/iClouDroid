package com.getup.iClouDroid.feature.photos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getup.iClouDroid.feature.photos.data.MonthBucketing
import com.getup.iClouDroid.feature.photos.data.db.PhotoMonthIndexEntity

/**
 * Floating year/month navigator (P2-016).
 *
 * Combines the two things the user asked for: a persistent "where am I" capsule (like Google
 * Photos' floating date pill) and a jump list of years (like iCloud Photos' sidebar). Tapping the
 * capsule expands the year list; the arrows step one month at a time.
 *
 * ## Anchoring
 *
 * Every read is driven by [currentYearMonth], which the caller derives from
 * `LazyListState.firstVisibleItemKey` — **never** from the visible index. That is the entire
 * P2-016 fix in one sentence: a month key is stable across list rebuilds, an integer index is not.
 *
 * @param currentYearMonth the month currently at the top of the viewport, or null before the first
 *   layout pass.
 * @param months all months in the gallery, newest first.
 * @param onJumpToMonth called with the target month; the caller resolves it to a list position.
 */
@Composable
fun MonthIndexBar(
    currentYearMonth: Int?,
    months: List<PhotoMonthIndexEntity>,
    onJumpToMonth: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (months.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    val years = remember(months) { months.map { MonthBucketing.yearOf(it.yearMonth) }.distinct() }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(end = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.End
    ) {
        if (currentYearMonth != null) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 3.dp,
                modifier = Modifier.clickable { expanded = !expanded }
            ) {
                Text(
                    text = MonthBucketing.labelOf(currentYearMonth),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .width(84.dp)
            ) {
                val listState = rememberLazyListState()
                // Keep the active year in view when the panel opens on a deep library.
                LaunchedEffect(expanded, currentYearMonth) {
                    if (!expanded || currentYearMonth == null) return@LaunchedEffect
                    val idx = years.indexOf(MonthBucketing.yearOf(currentYearMonth))
                    if (idx >= 0) listState.scrollToItem(idx)
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(years, key = { it }) { year ->
                        val selected = currentYearMonth != null &&
                            MonthBucketing.yearOf(currentYearMonth) == year
                        Text(
                            text = year.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier
                                .clickable {
                                    val target = months.firstOrNull {
                                        MonthBucketing.yearOf(it.yearMonth) == year
                                    }
                                    if (target != null) {
                                        expanded = false
                                        onJumpToMonth(target.yearMonth)
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Month stepper. Kept separate from the capsule so a stray tap while scrolling does not
        // open the year panel.
        Column(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StepperButton(
                icon = Icons.Default.KeyboardArrowUp,
                description = "上一月",
                enabled = currentYearMonth != null && newerMonthOf(months, currentYearMonth) != null,
                onClick = { currentYearMonth?.let { newerMonthOf(months, it) }?.let(onJumpToMonth) }
            )
            StepperButton(
                icon = Icons.Default.KeyboardArrowDown,
                description = "下一月",
                enabled = currentYearMonth != null && olderMonthOf(months, currentYearMonth) != null,
                onClick = { currentYearMonth?.let { olderMonthOf(months, it) }?.let(onJumpToMonth) }
            )
        }
    }
}

/** The month above the current one (newer), or null at the top of the list. */
private fun newerMonthOf(months: List<PhotoMonthIndexEntity>, current: Int): Int? {
    val idx = months.indexOfFirst { it.yearMonth == current }
    if (idx <= 0) return null
    return months[idx - 1].yearMonth
}

/** The month below the current one (older), or null at the bottom of the list. */
private fun olderMonthOf(months: List<PhotoMonthIndexEntity>, current: Int): Int? {
    val idx = months.indexOfFirst { it.yearMonth == current }
    if (idx < 0 || idx >= months.lastIndex) return null
    return months[idx + 1].yearMonth
}

@Composable
private fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(
                color = if (enabled) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                },
                shape = CircleShape
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                },
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
