package com.nearbyspot.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private const val CAROUSEL_ITEM_WIDTH_FRACTION = 0.86f

/** Floating rounded card pinned below the status bar, used for map screen controls. */
@Composable
fun MapHeaderCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            content = content,
        )
    }
}

/**
 * Bottom tray over a map with a title row and arbitrary content (status message or carousel).
 */
@Composable
fun MapResultsTray(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(top = 18.dp, bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                actions()
            }
            content()
        }
    }
}

/** Snapping horizontal carousel where each item fills most of the width so the next one peeks. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> PlaceCarousel(
    items: List<T>,
    itemKey: (T) -> Any,
    state: LazyListState,
    modifier: Modifier = Modifier,
    itemContent: @Composable (index: Int, item: T, itemModifier: Modifier) -> Unit,
) {
    LazyRow(
        state = state,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        flingBehavior = rememberSnapFlingBehavior(lazyListState = state),
    ) {
        itemsIndexed(items = items, key = { _, item -> itemKey(item) }) { index, item ->
            itemContent(index, item, Modifier.fillParentMaxWidth(CAROUSEL_ITEM_WIDTH_FRACTION))
        }
    }
}
