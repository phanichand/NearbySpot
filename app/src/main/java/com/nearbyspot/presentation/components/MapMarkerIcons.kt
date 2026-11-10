package com.nearbyspot.presentation.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/** A marker bitmap plus the anchor that keeps its tip (or centre) on the coordinate. */
data class MapMarkerIcon(
    val descriptor: BitmapDescriptor,
    val anchor: Offset,
)

/**
 * A theme-coloured teardrop pin. With a [label] it shows the rank number so a pin can be
 * matched to its card; without one it shows a centre dot. Call only inside `GoogleMap`
 * content, where the Maps SDK is initialised.
 */
@Composable
fun rememberPlacePinIcon(
    label: String? = null,
    highlighted: Boolean = false,
): MapMarkerIcon {
    val colors = MaterialTheme.colorScheme
    val fill = if (highlighted) colors.tertiary else colors.primary
    val content = if (highlighted) colors.onTertiary else colors.onPrimary
    return rememberPinIcon(
        label = label,
        fill = fill,
        content = content,
        radiusDp = if (highlighted) PIN_RADIUS_HIGHLIGHTED_DP else PIN_RADIUS_DP,
    )
}

/** The trip destination: a dark pin that stands apart from the green stop pins. */
@Composable
fun rememberDestinationPinIcon(): MapMarkerIcon {
    val colors = MaterialTheme.colorScheme
    return rememberPinIcon(
        label = null,
        fill = colors.inverseSurface,
        content = colors.inverseOnSurface,
        radiusDp = PIN_RADIUS_HIGHLIGHTED_DP,
    )
}

/** A round waypoint dot for the trip start (hollow) or the traveller's position (filled). */
@Composable
fun rememberWaypointDotIcon(filled: Boolean): MapMarkerIcon {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current.density
    val ring = colors.primary
    val center = if (filled) colors.primary else colors.surfaceContainerLowest
    val halo = colors.surfaceContainerLowest
    return remember(filled, ring, center, halo, density) {
        MapMarkerIcon(
            descriptor = BitmapDescriptorFactory.fromBitmap(
                drawWaypointDot(density, ring.toArgb(), center.toArgb(), halo.toArgb()),
            ),
            anchor = Offset(0.5f, 0.5f),
        )
    }
}

@Composable
private fun rememberPinIcon(
    label: String?,
    fill: Color,
    content: Color,
    radiusDp: Float,
): MapMarkerIcon {
    val density = LocalDensity.current.density
    return remember(label, fill, content, radiusDp, density) {
        val pin = drawPin(
            density = density,
            radiusPx = radiusDp * density,
            fillArgb = fill.toArgb(),
            contentArgb = content.toArgb(),
            label = label,
        )
        MapMarkerIcon(
            descriptor = BitmapDescriptorFactory.fromBitmap(pin.bitmap),
            anchor = Offset(0.5f, pin.tipFraction),
        )
    }
}

private class PinBitmap(val bitmap: Bitmap, val tipFraction: Float)

private fun drawPin(
    density: Float,
    radiusPx: Float,
    fillArgb: Int,
    contentArgb: Int,
    label: String?,
): PinBitmap {
    val margin = PIN_SHADOW_MARGIN_DP * density
    val strokeWidth = PIN_STROKE_DP * density
    val centerX = margin + radiusPx
    val centerY = margin + radiusPx
    val tipY = centerY + radiusPx * PIN_TIP_DISTANCE_RATIO
    val width = ceil(centerX * 2).toInt()
    val height = ceil(tipY + margin).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // The tail leaves the circle where a line from the tip is tangent to it, so the
    // outline has no kink.
    val tangentAngle = Math.toDegrees(asin(1.0 / PIN_TIP_DISTANCE_RATIO)).toFloat()
    val startAngle = 180f - tangentAngle
    val sweepAngle = 360f - 2f * (90f - tangentAngle)
    val startRadians = Math.toRadians(startAngle.toDouble())
    val outline = Path().apply {
        moveTo(centerX, tipY)
        lineTo(
            centerX + radiusPx * cos(startRadians).toFloat(),
            centerY + radiusPx * sin(startRadians).toFloat(),
        )
        arcTo(
            RectF(centerX - radiusPx, centerY - radiusPx, centerX + radiusPx, centerY + radiusPx),
            startAngle,
            sweepAngle,
        )
        close()
    }

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillArgb
        style = Paint.Style.FILL
        setShadowLayer(margin * 0.6f, 0f, density, PIN_SHADOW_ARGB)
    }
    canvas.drawPath(outline, fillPaint)

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = contentArgb
        style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
        strokeJoin = Paint.Join.ROUND
    }
    canvas.drawPath(outline, strokePaint)

    val contentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = contentArgb
        style = Paint.Style.FILL
    }
    if (label == null) {
        canvas.drawCircle(centerX, centerY, radiusPx * PIN_DOT_RATIO, contentPaint)
    } else {
        contentPaint.typeface = Typeface.DEFAULT_BOLD
        contentPaint.textAlign = Paint.Align.CENTER
        contentPaint.textSize = radiusPx * PIN_LABEL_RATIO
        val baseline = centerY - (contentPaint.ascent() + contentPaint.descent()) / 2f
        canvas.drawText(label, centerX, baseline, contentPaint)
    }

    return PinBitmap(bitmap = bitmap, tipFraction = tipY / height)
}

private fun drawWaypointDot(
    density: Float,
    ringArgb: Int,
    centerArgb: Int,
    haloArgb: Int,
): Bitmap {
    val margin = PIN_SHADOW_MARGIN_DP * density
    val radius = WAYPOINT_RADIUS_DP * density
    val size = ceil((radius + margin) * 2).toInt()
    val center = size / 2f
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = haloArgb
        setShadowLayer(margin * 0.6f, 0f, density, PIN_SHADOW_ARGB)
    }
    canvas.drawCircle(center, center, radius, paint)
    paint.clearShadowLayer()
    paint.color = ringArgb
    canvas.drawCircle(center, center, radius * 0.78f, paint)
    paint.color = centerArgb
    canvas.drawCircle(center, center, radius * 0.4f, paint)
    return bitmap
}

private const val PIN_RADIUS_DP = 15f
private const val PIN_RADIUS_HIGHLIGHTED_DP = 19f
private const val PIN_STROKE_DP = 2f
private const val PIN_SHADOW_MARGIN_DP = 4f
private const val PIN_TIP_DISTANCE_RATIO = 1.5f
private const val PIN_DOT_RATIO = 0.36f
private const val PIN_LABEL_RATIO = 1.05f
private const val PIN_SHADOW_ARGB = 0x55000000
private const val WAYPOINT_RADIUS_DP = 9f
