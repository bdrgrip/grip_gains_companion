package app.grip_gains_companion.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

@Composable
fun RepMarkersOverlay(
    repTimestamps: List<Double>?, // Null-safe
    minTime: Double,
    maxTime: Double,
    modifier: Modifier = Modifier
) {
    // Basic safety gating
    if (repTimestamps.isNullOrEmpty() || maxTime <= minTime) return

    val markerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val timeRange = (maxTime - minTime).toFloat()

        repTimestamps.forEach { repTime ->
            // Logic: Linear interpolation is the safest way to map time to X position
            if (repTime in minTime..maxTime) {
                val xPos = ((repTime - minTime).toFloat() / timeRange) * width

                drawLine(
                    color = markerColor,
                    start = Offset(xPos, 0f),
                    end = Offset(xPos, height),
                    strokeWidth = 3f
                )
            }
        }
    }
}