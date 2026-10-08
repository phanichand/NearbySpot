package com.nearbyspot.presentation.detail

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nearbyspot.domain.model.Place
import com.nearbyspot.domain.model.RankedPlace
import com.nearbyspot.domain.model.Review
import com.nearbyspot.presentation.components.RatingBar
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
    PlaceDetailSheet(
        place = rankedPlace.place,
        travelMinutes = rankedPlace.travelInfo.durationMinutes.roundToInt(),
        travelLabel = "${rankedPlace.travelInfo.durationMinutes.roundToInt()} min away",
        distanceMeters = rankedPlace.travelInfo.distanceMeters,
        onDismiss = onDismiss,
        photoProvider = photoProvider,
        isFavorite = isFavorite,
        onToggleFavorite = onToggleFavorite,
        onFetchReviews = onFetchReviews,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
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
                PlaceHeroPhoto(placeId = place.placeId, photoProvider = photoProvider)
                Spacer(Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                if (onToggleFavorite != null) {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = if (isFavorite) "Remove from saved" else "Save place",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (place.isOpen != null) {
                    Spacer(Modifier.width(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Circle,
                            contentDescription = if (place.isOpen) "Currently open" else "Currently closed",
                            modifier = Modifier.size(10.dp),
                            tint = if (place.isOpen) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (place.isOpen) "Open" else "Closed",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (place.isOpen) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                RatingBar(rating = place.rating, starSize = 20.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${place.rating}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "(${place.userRatingCount} reviews)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.AccessTime,
                    contentDescription = "Travel time",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = travelLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (distanceMeters > 0) {
                    Spacer(Modifier.width(12.dp))
                    val distanceText = if (distanceMeters >= 1000) {
                        "%.1f km".format(distanceMeters / 1000.0)
                    } else {
                        "$distanceMeters m"
                    }
                    Text(
                        text = distanceText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (detourLabel != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = detourLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            if (place.priceLevel != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Price: ${"$".repeat(place.priceLevel)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = "Address",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = place.address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val gmmUri = Uri.parse(
                        "google.navigation:q=${place.latitude},${place.longitude}"
                    )
                    val mapsIntent = Intent(Intent.ACTION_VIEW, gmmUri).apply {
                        setPackage("com.google.android.apps.maps")
                    }
                    if (mapsIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(mapsIntent)
                    } else {
                        val fallbackUri = Uri.parse(
                            "geo:${place.latitude},${place.longitude}?q=${place.latitude},${place.longitude}(${Uri.encode(place.name)})"
                        )
                        context.startActivity(Intent(Intent.ACTION_VIEW, fallbackUri))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Navigation,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Navigate")
            }

            if (reviews.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Top Reviews",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                reviews.take(5).forEachIndexed { index, review ->
                    ReviewCard(review = review)
                    if (index < reviews.size - 1 && index < 4) {
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(review: Review) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
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
                    style = MaterialTheme.typography.labelMedium,
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
private fun PlaceHeroPhoto(placeId: String, photoProvider: PlacePhotoProvider) {
    var bitmap by remember(placeId) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(placeId) { mutableStateOf(true) }

    LaunchedEffect(placeId) {
        loading = true
        bitmap = runCatching { photoProvider.getPhoto(placeId) }.getOrNull()
        loading = false
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
            bitmap != null -> bitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Place photo",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}
