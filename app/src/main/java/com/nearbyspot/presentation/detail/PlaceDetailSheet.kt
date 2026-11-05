package com.nearbyspot.presentation.detail

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.nearbyspot.domain.model.Place
import com.nearbyspot.domain.model.RankedPlace
import com.nearbyspot.domain.model.Review
import com.nearbyspot.domain.model.TransportMode
import com.nearbyspot.presentation.components.RatingBar
import com.nearbyspot.presentation.theme.StarYellow
import com.nearbyspot.presentation.util.DistanceFormatter
import com.nearbyspot.util.MapsNavigationUrl
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
    tripDestination: LatLng? = null,
    transportMode: TransportMode = TransportMode.DRIVE,
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
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (onToggleFavorite != null) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (isFavorite) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    ) {
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = if (isFavorite) "Remove from saved" else "Save place",
                                tint = if (isFavorite) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RatingBar(rating = place.rating, starSize = 20.dp)
                Text(
                    text = "${place.rating} (${place.userRatingCount} reviews)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))

            if (place.isOpen != null || place.priceLevel != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    place.isOpen?.let { isOpen ->
                        DetailBadge(
                            text = if (isOpen) "Open now" else "Closed now",
                            containerColor = if (isOpen) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                            contentColor = if (isOpen) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                        )
                    }
                    place.priceLevel?.let {
                        DetailBadge(text = "Price ${"$".repeat(it)}")
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (travelLabel.isNotBlank() || detourLabel != null || distanceMeters > 0) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.AccessTime,
                            contentDescription = "Travel time",
                            modifier = Modifier.padding(start = 16.dp).size(22.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.padding(vertical = 14.dp)) {
                            if (travelLabel.isNotBlank()) {
                                Text(
                                    text = travelLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (detourLabel != null) {
                                Text(
                                    text = detourLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                            if (distanceMeters > 0) {
                                Text(
                                    text = DistanceFormatter.format(distanceMeters),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (place.address.isNotBlank()) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Filled.Place,
                        contentDescription = null,
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
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val gmmUri = Uri.parse(
                        if (tripDestination != null) {
                            MapsNavigationUrl.build(
                                destination = tripDestination,
                                waypoint = LatLng(place.latitude, place.longitude),
                                mode = transportMode,
                            )
                        } else {
                            "google.navigation:q=${place.latitude},${place.longitude}"
                        },
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
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
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
private fun DetailBadge(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}

@Composable
private fun ReviewCard(review: Review) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
        bitmap = try {
            photoProvider.getPhoto(placeId)
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
        loading = false
    }

    if (loading || bitmap != null) {
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
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
