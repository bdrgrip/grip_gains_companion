package app.grip_gains_companion.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.grip_gains_companion.model.ForceHistoryEntry
import app.grip_gains_companion.ui.theme.GripGainsTheme

@Composable
fun ForceGraph(
    forceHistory: List<ForceHistoryEntry>,
    useLbs: Boolean,
    windowSeconds: Int = 5,
    targetWeight: Double? = null,
    tolerance: Double? = null,
    isReconnecting: Boolean = false
) {
    // Force dark mode for this specific component so the colors always match the web app
    GripGainsTheme(darkTheme = true) {
        val primaryColor = MaterialTheme.colorScheme.primary
        val targetLineColor = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        val toleranceBandColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(Color(0xFF1A2231)) // Hardcoded Grip Gains background
                .padding(vertical = 8.dp)
        ) {
            if (forceHistory.isEmpty()) return@Canvas

            val now = forceHistory.last().timestamp.time
            val windowMs = windowSeconds * 1000L
            val cutoff = now - windowMs

            // Filter to visible window
            val visibleHistory = forceHistory.filter { it.timestamp.time >= cutoff }
            if (visibleHistory.isEmpty()) return@Canvas

            val maxForceInWindow = visibleHistory.maxOfOrNull { it.force } ?: 1.0

            // Calculate Y-axis bounds dynamically, anchoring at 0
            val yMax = if (targetWeight != null) {
                val targetWithTol = targetWeight + (tolerance ?: 0.0)
                maxOf(maxForceInWindow, targetWithTol) * 1.2f // 20% headroom
            } else {
                maxOf(maxForceInWindow, 5.0) * 1.2f
            }

            // Draw Target Band
            if (targetWeight != null) {
                val targetY = size.height - ((targetWeight / yMax) * size.height).toFloat()

                if (tolerance != null) {
                    val topY = size.height - (((targetWeight + tolerance) / yMax) * size.height).toFloat()
                    val bottomY = size.height - (((targetWeight - tolerance) / yMax) * size.height).toFloat()

                    // Tolerance fill
                    drawRect(
                        color = toleranceBandColor,
                        topLeft = Offset(0f, topY),
                        size = Size(size.width, bottomY - topY)
                    )
                }

                // Target dashed line
                drawLine(
                    color = targetLineColor,
                    start = Offset(0f, targetY),
                    end = Offset(size.width, targetY),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            }

            // Draw Zero Line
            drawLine(
                color = gridColor,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx()
            )

            // Draw Live Force Path
            val path = Path()
            var pathStarted = false

            visibleHistory.forEach { entry ->
                val timeOffset = entry.timestamp.time - cutoff
                val x = (timeOffset.toFloat() / windowMs.toFloat()) * size.width
                val y = size.height - ((entry.force / yMax) * size.height).toFloat()

                if (!pathStarted) {
                    path.moveTo(x, y)
                    pathStarted = true
                } else {
                    path.lineTo(x, y)
                }
            }

            val lineColor = if (isReconnecting) Color.Gray else primaryColor

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}