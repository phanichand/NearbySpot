package com.nearbyspot.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nearbyspot.domain.model.Category

@Composable
fun CategoryChipRow(
    selectedCategories: Set<Category>,
    onCategoryToggled: (Category) -> Unit,
    modifier: Modifier = Modifier,
    edgePadding: Dp = 16.dp,
) {
    val scrollState = rememberScrollState()
    val fadeColor = MaterialTheme.colorScheme.surface

    Row(
        modifier = modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                val fadeWidth = 24.dp.toPx()
                if (scrollState.value > 0) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to fadeColor.copy(alpha = 0f),
                            1f to fadeColor,
                            startX = 0f,
                            endX = fadeWidth,
                        ),
                        blendMode = BlendMode.DstIn,
                        size = size.copy(width = fadeWidth),
                    )
                }
                if (scrollState.value < scrollState.maxValue) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to fadeColor,
                            1f to fadeColor.copy(alpha = 0f),
                            startX = size.width - fadeWidth,
                            endX = size.width,
                        ),
                        blendMode = BlendMode.DstIn,
                        topLeft = Offset(size.width - fadeWidth, 0f),
                        size = size.copy(width = fadeWidth),
                    )
                }
            }
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.width(edgePadding - 8.dp))
        Category.entries.forEach { category ->
            val selected = category in selectedCategories
            FilterChip(
                selected = selected,
                onClick = { onCategoryToggled(category) },
                label = { Text(category.label) },
                leadingIcon = {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
            )
        }
        Spacer(Modifier.width(edgePadding - 8.dp))
    }
}
