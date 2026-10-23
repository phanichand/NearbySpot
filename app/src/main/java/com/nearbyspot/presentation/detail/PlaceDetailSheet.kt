package com.nearbyspot.presentation.detail

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nearbyspot.R
import com.nearbyspot.domain.model.Place
import com.nearbyspot.domain.model.RankedPlace
import com.nearbyspot.domain.model.Review
import com.nearbyspot.presentation.components.MetaPill
import com.nearbyspot.presentation.components.OpenStatusPill
import com.nearbyspot.presentation.components.RatingBar
import com.nearbyspot.presentation.components.formatDistance
import com.nearbyspot.presentation.components.formatPriceLevel
import com.nearbyspot.presentation.components.formatRating
import com.nearbyspot.presentation.components.formatReviewCount
import com.nearbyspot.presentation.theme.StarYellow
import com.nearbyspot.util.PlacePhotoProvider
import kotlin.math.roundToInt

/**
 * Overload for backward compatibility with HomeScreen's RankedPlace.
 */
@Composable
fun PlaceDetailSheet(
    rankedPlace: RankedPlace,
    onDismiss: () -> Unit,
    photoProvider: PlacePhotoProvider? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onFetchReviews: (suspend (String) -> List<Review>)? = null,
) {
    val travelMinutes = rankedPlace.travelInfo.durationMinutes.roundToInt()
    PlaceDetailSheet(
        place = rankedPlace.place,
        travelMinutes = travelMinutes,
        travelLabel = stringResource(R.string.place_minutes_away, travelMinutes),
        distanceMeters = rankedPlace.travelInfo.distanceMeters,
        onDismiss = onDismiss,
        photoProvider = photoProvider,
        isFavorite = isFavorite,
        onToggleFavorite = onToggleFavorite,
        onFetchReviews = onFetchReviews,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlaceDetailSheet(
    place: Place,
    travelMinutes: Int,
    travelLabel: String,
    distanceMeters: Int,
    detourLabel: String? = null,
    onDismiss: () -> Unit,
    photoProvider: PlacePhotoProvider? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onFetchReviews: (suspend (String) -> List<Review>)? = null,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val colors = MaterialTheme.colorScheme

    var reviews by remember(place.placeId) { mutableStateOf(place.reviews) }
    LaunchedEffect(place.placeId) {
        if (reviews.isEmpty() && onFetchReviews != null) {
            reviews = onFetchReviews(place.placeId)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            if (photoProvider != null && place.photoRefs.isNotEmpty()) {
                PlaceHeroPhoto(
                    placeId = place.placeId,
                    placeName = place.name,
                    photoProvider = photoProvider,
                )
            }

            Text(
                text = place.name,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RatingBar(rating = place.rating, starSize = 18.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatRating(place.rating),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurface,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = pluralStringResource(
                        R.plurals.place_review_count,
                        place.userRatingCount,
                        formatReviewCount(place.userRatingCount),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (travelLabel.isNotBlank()) {
                    MetaPill(
                        text = travelLabel,
                        icon = Icons.Filled.AccessTime,
                        containerColor = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer,
                    )
                }
                if (distanceMeters > 0) {
                    MetaPill(text = formatDistance(distanceMeters), icon = Icons.Filled.Straighten)
                }
                if (detourLabel != null) {
                    MetaPill(
                        text = detourLabel,
                        icon = Icons.Filled.Route,
                        containerColor = colors.tertiaryContainer,
                        contentColor = colors.onTertiaryContainer,
                    )
                }
                if (place.priceLevel != null) {
                    MetaPill(text = formatPriceLevel(place.priceLevel))
                }
                if (place.isOpen != null) {
                    OpenStatusPill(isOpen = place.isOpen)
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(16.dp),
                    tint = colors.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = place.address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { launchNavigation(context, place) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Navigation,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_navigate))
                }
                if (onToggleFavorite != null) {
                    OutlinedButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 14.dp),
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(if (isFavorite) R.string.action_saved else R.string.action_save),
                        )
                    }
                }
            }

            if (reviews.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                Text(
                    text = stringResource(R.string.place_top_reviews),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    reviews.take(MAX_REVIEWS).forEach { review ->
                        ReviewCard(review = review)
                    }
                }
            }
        }
    }
}

private fun launchNavigation(context: Context, place: Place) {
    val gmmUri = Uri.parse("google.navigation:q=${place.latitude},${place.longitude}")
    val mapsIntent = Intent(Intent.ACTION_VIEW, gmmUri).apply {
        setPackage("com.google.android.apps.maps")
    }
    if (mapsIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(mapsIntent)
    } else {
        val fallbackUri = Uri.parse(
            "geo:${place.latitude},${place.longitude}?q=${place.latitude},${place.longitude}(${Uri.encode(place.name)})",
        )
        context.startActivity(Intent(Intent.ACTION_VIEW, fallbackUri))
    }
}

@Composable
private fun ReviewCard(review: Review) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = review.authorName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Review rating: ${review.rating}",
                        modifier = Modifier.size(14.dp),
                        tint = StarYellow,
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = "${review.rating}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            if (review.relativeTimeDescription.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = review.relativeTimeDescription,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (review.text.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = review.text,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PlaceHeroPhoto(placeId: String, placeName: String, photoProvider: PlacePhotoProvider) {
    var bitmap by remember(placeId) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(placeId) { mutableStateOf(true) }

    LaunchedEffect(placeId) {
        loading = true
        bitmap = runCatching { photoProvider.getPhoto(placeId) }.getOrNull()
        loading = false
    }

    // Collapse entirely when no photo could be loaded instead of leaving an empty frame.
    if (!loading && bitmap == null) return

    Box(
        modifier = Modifier
            .padding(bottom = 16.dp)
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp == null) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = stringResource(R.string.place_photo, placeName),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

private const val MAX_REVIEWS = 5
