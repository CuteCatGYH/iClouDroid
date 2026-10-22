package com.getup.iClouDroid.feature.photos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.getup.iClouDroid.feature.photos.data.db.PhotoAssetEntity

/** Gap between grid cells, in dp. Kept as a constant so height estimation stays exact. */
private val GRID_SPACING = 1.dp

/**
 * One month's photos, rendered as a self-contained fixed-height grid (P2-016).
 *
 * ## Why this shape
 *
 * The gallery is a `LazyColumn` of months, and each month is a nested `LazyVerticalGrid`. That
 * nesting needs two non-obvious settings or it degrades badly:
 *
 *  1. **`userScrollEnabled` must be false.** Otherwise the inner grid consumes the vertical
 *     gestures and the outer list cannot scroll.
 *  2. **The height must be known up front.** A `LazyVerticalGrid` inside a `LazyColumn` is
 *     measured with unbounded height; without an explicit `Modifier.height` it tries to measure
 *     every child before it can report a size, which for a month with a thousand photos means a
 *     multi-second frame. Passing the computed height lets it stay virtualised.
 *
 * [estimateHeight] is exact rather than conservative because [PhotoGridItem] is always square
 * (`aspectRatio(1f)`) with a fixed 1dp gap — there is no variable-height content to guess at.
 *
 * ## Future: day sections
 *
 * This is deliberately the *only* place that knows how a month is laid out. If the gallery later
 * needs Google-Photos-style per-day grouping inside a month, this component is the single file to
 * change — the data layer already carries `assetDate`, so no schema change is required.
 */
@Composable
fun MonthGrid(
    photos: List<PhotoAssetEntity>,
    columns: Int,
    onPhotoClick: (PhotoAssetEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.height(estimateHeight(photos.size, columns)),
        userScrollEnabled = false,
        contentPadding = PaddingValues(0.dp),
        horizontalArrangement = Arrangement.spacedBy(GRID_SPACING),
        verticalArrangement = Arrangement.spacedBy(GRID_SPACING)
    ) {
        items(photos, key = { it.id }) { photo ->
            PhotoGridItem(photo = photo, onClick = { onPhotoClick(photo) })
        }
    }
}

/**
 * Exact height of a month grid.
 *
 * Computed by hand rather than by asking the layout system, because the whole point is to avoid
 * measuring the children. Each cell is square, so its height is `width / columns`, and the width is
 * the one the parent hands down.
 */
@Composable
private fun estimateHeight(itemCount: Int, columns: Int): Dp {
    if (itemCount <= 0) return 0.dp
    val width = LocalConfiguration.current.screenWidthDp.dp
    val rows = (itemCount + columns - 1) / columns
    val cell = width / columns
    val gaps = GRID_SPACING * (rows - 1).coerceAtLeast(0)
    return cell * rows + gaps
}
