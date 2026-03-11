package app.grip_gains_companion.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
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
import kotlinx.coroutines.isActive

@Composable
fun ForceGraph(
    forceHistory: List<ForceHistoryEntry>,
    useLbs: Boolean,
    windowSeconds: Int = 5,
    targetWeight: Double? = null,
    tolerance: Double? = null,
    isReconnecting: Boolean = false
) {
    GripGainsTheme(darkTheme = true) {
        val primaryColor = MaterialTheme.colorScheme.primary
        val targetLineColor = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        val toleranceBandColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)

        // 1. THE SPRING-LOADED MOMENTUM VALUE
        val latestForce = forceHistory.lastOrNull()?.force?.toFloat() ?: 0f
        val animatedLiveForce by animateFloatAsState(
            targetValue = latestForce,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 40f), // Soft, fluid momentum
            label = "liveEdge"
        )

        // 2. THE 60FPS VISUAL TRAIL
        // This decouples the drawing from the slow hardware packets
        val visualHistory = remember { ArrayList<Pair<Long, Float>>() }

        // Explicitly clear the visual trail only when the real data is reset
        // (This fixes the bug where the graph wouldn't draw immediately on reconnect!)
        LaunchedEffect(forceHistory.isEmpty()) {
            if (forceHistory.isEmpty()) {
                visualHistory.clear()
            }
        }

        var currentFrameTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

        LaunchedEffect(Unit) {
            while (isActive) {
                withFrameMillis {
                    val now = System.currentTimeMillis()
                    currentFrameTime = now

                    // ALWAYS add to the visual trail. Because this component only exists
                    // when connected, we want to instantly start drawing the line at 0.0!
                    visualHistory.add(Pair(now, animatedLiveForce))

                    // Prune old pixels hanging off the left edge of the screen
                    val cutoff = now - (windowSeconds * 1000L) - 500L
                    while (visualHistory.isNotEmpty() && visualHistory.first().first < cutoff) {
                        visualHistory.removeAt(0)
                    }
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(top = 8.dp, bottom = 24.dp)
        ) {
            if (visualHistory.isEmpty() || forceHistory.isEmpty()) return@Canvas

            val now = currentFrameTime
            val windowMs = windowSeconds * 1000L
            val cutoff = now - windowMs

            // Grab the visible points PLUS one point just off-screen to the left to prevent popping
            val firstVisibleIndex = visualHistory.indexOfFirst { it.first >= cutoff }
            val startIndex = if (firstVisibleIndex > 0) firstVisibleIndex - 1 else 0

            if (startIndex < 0 || startIndex >= visualHistory.size) return@Canvas
            val visibleHistory = visualHistory.subList(startIndex, visualHistory.size)
            if (visibleHistory.isEmpty()) return@Canvas

            val maxForceInWindow = visibleHistory.maxOfOrNull { it.second } ?: 1.0f

            val yMax = if (targetWeight != null) {
                val targetWithTol = targetWeight.toFloat() + (tolerance?.toFloat() ?: 0.0f)
                maxOf(maxForceInWindow, targetWithTol) * 1.2f
            } else {
                maxOf(maxForceInWindow, 5.0f) * 1.2f
            }

            if (targetWeight != null) {
                val targetY = size.height - ((targetWeight.toFloat() / yMax) * size.height)

                if (tolerance != null) {
                    val topY = size.height - (((targetWeight.toFloat() + tolerance.toFloat()) / yMax) * size.height)
                    val bottomY = size.height - (((targetWeight.toFloat() - tolerance.toFloat()) / yMax) * size.height)

                    drawRect(
                        color = toleranceBandColor,
                        topLeft = Offset(0f, topY),
                        size = Size(size.width, bottomY - topY)
                    )
                }

                drawLine(
                    color = targetLineColor,
                    start = Offset(0f, targetY),
                    end = Offset(size.width, targetY),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            }

            drawLine(
                color = gridColor,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx()
            )

            // --- DRAWING THE BUTTERY TRAIL ---
            val path = Path()
            var pathStarted = false

            visibleHistory.forEach { entry ->
                val timeOffset = entry.first - cutoff
                val x = (timeOffset.toFloat() / windowMs.toFloat()) * size.width
                val y = size.height - ((entry.second / yMax) * size.height)

                if (!pathStarted) {
                    path.moveTo(x, y)
                    pathStarted = true
                } else {
                    // Because our visual array has a point every 16 milliseconds,
                    // drawing a straight line between them automatically creates a perfectly smooth curve!
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