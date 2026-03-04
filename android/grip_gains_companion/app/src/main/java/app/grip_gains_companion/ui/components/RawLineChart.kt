package app.grip_gains_companion.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun RawLineChart(
    times: List<Double>,
    primary: List<Double>,
    secondary: List<Double>?,
    pColor: Color,
    sColor: Color = Color.Gray,
    connectGaps: Boolean = false // NEW: Allows cumulative data to bridge rests
) {
    val textMeasurer = rememberTextMeasurer()

    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    val repTextStyle = TextStyle(color = onSurfaceColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    val restTextStyle = TextStyle(color = onSurfaceVariantColor, fontSize = 10.sp)
    val gridLineColor = onSurfaceColor.copy(alpha = 0.3f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
    ) {
        if (times.isEmpty()) return@Canvas

        val maxP = (primary.filter { !it.isNaN() }.maxOrNull()?.toFloat() ?: 1f).coerceAtLeast(0.1f) * 1.3f
        val maxS = (secondary?.filter { !it.isNaN() }?.maxOrNull()?.toFloat() ?: 1f).coerceAtLeast(0.1f) * 1.3f

        val bottomPadding = 20.dp.toPx()
        val graphHeight = size.height - bottomPadding

        // 1. COMPRESS THE TIMELINE
        val compressedTimes = FloatArray(times.size)
        val repSegments = mutableListOf<Triple<Int, Int, Float>>()
        val restGaps = mutableListOf<Pair<Float, Float>>()

        var currentComp = 0f
        var blockStart = 0
        val visualGap = 1.0f

        compressedTimes[0] = 0f
        for (i in 1 until times.size) {
            val dt = (times[i] - times[i - 1]).toFloat()
            if (dt > 0.5f) {
                repSegments.add(Triple(blockStart, i - 1, compressedTimes[blockStart]))
                val gapMid = currentComp + (visualGap / 2f)
                restGaps.add(Pair(gapMid, dt))

                currentComp += visualGap
                blockStart = i
            } else {
                currentComp += dt
            }
            compressedTimes[i] = currentComp
        }
        repSegments.add(Triple(blockStart, times.size - 1, compressedTimes[blockStart]))

        val maxComp = currentComp.coerceAtLeast(1f)
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

        // 2. DRAW ACTIVE REP LABELS
        repSegments.forEach { (start, end, compStart) ->
            val realDuration = (times[end] - times[start]).toFloat()
            if (realDuration > 0.5f) {
                val compEnd = compressedTimes[end]
                val midComp = compStart + (compEnd - compStart) / 2f
                val midX = (midComp / maxComp) * size.width

                val text = "${realDuration.roundToInt()}s"
                val textLayout = textMeasurer.measure(text, repTextStyle)
                val drawX = (midX - (textLayout.size.width / 2f)).coerceIn(0f, size.width - textLayout.size.width.toFloat())

                drawText(
                    textMeasurer = textMeasurer,
                    text = text,
                    style = repTextStyle,
                    topLeft = Offset(drawX, size.height - textLayout.size.height.toFloat())
                )
            }
        }

        // 3. DRAW REST DIVIDERS
        restGaps.forEach { (compMid, realDuration) ->
            val lineX = (compMid / maxComp) * size.width

            drawLine(
                color = gridLineColor,
                start = Offset(lineX, 0f),
                end = Offset(lineX, graphHeight),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = dashEffect
            )

            val restText = "${realDuration.roundToInt()}s rest"
            val restLayout = textMeasurer.measure(restText, restTextStyle)
            val restDrawX = (lineX - (restLayout.size.width / 2f)).coerceIn(0f, size.width - restLayout.size.width.toFloat())

            drawText(
                textMeasurer = textMeasurer,
                text = restText,
                style = restTextStyle,
                topLeft = Offset(restDrawX, 4.dp.toPx())
            )
        }

        // 4. DRAW THE PATHS
        secondary?.let { drawSmoothDataPath(compressedTimes, times, it, maxComp, maxS, sColor, graphHeight, connectGaps) }
        drawSmoothDataPath(compressedTimes, times, primary, maxComp, maxP, pColor, graphHeight, connectGaps)
    }
}

private fun DrawScope.drawSmoothDataPath(
    compressedTimes: FloatArray,
    times: List<Double>,
    values: List<Double>,
    maxX: Float,
    maxY: Float,
    color: Color,
    graphHeight: Float,
    connectGaps: Boolean
) {
    val smoothedValues = values.mapIndexed { i, v ->
        if (v.isNaN()) v else {
            var sum = 0.0
            var count = 0
            for (j in maxOf(0, i - 2)..minOf(values.size - 1, i + 2)) {
                if (!values[j].isNaN()) {
                    // Prevent smoothing from looking across large rest gaps
                    if (kotlin.math.abs(times[i] - times[j]) < 1.0) {
                        sum += values[j]
                        count++
                    }
                }
            }
            if (count > 0) sum / count else v
        }
    }

    val segments = mutableListOf<List<Offset>>()
    var currentSegment = mutableListOf<Offset>()

    smoothedValues.forEachIndexed { i, v ->
        val isTimeGap = i > 0 && (compressedTimes[i] - compressedTimes[i-1] > 0.5f)

        // If connectGaps is true, we ignore the time gap and keep building the line
        val shouldBreak = v.isNaN() || (isTimeGap && !connectGaps)

        if (shouldBreak) {
            if (currentSegment.isNotEmpty()) {
                segments.add(currentSegment)
                currentSegment = mutableListOf()
            }
        }

        if (!v.isNaN()) {
            val x = (compressedTimes[i] / maxX) * size.width
            val y = (graphHeight - ((v.toFloat() / maxY) * graphHeight)).coerceAtMost(graphHeight - 2f)
            currentSegment.add(Offset(x, y))
        }
    }
    if (currentSegment.isNotEmpty()) {
        segments.add(currentSegment)
    }

    segments.forEach { segment ->
        if (segment.size < 2) return@forEach

        val linePath = Path()
        linePath.moveTo(segment.first().x, segment.first().y)

        var prev = segment.first()
        for (i in 1 until segment.size) {
            val curr = segment[i]
            val midX = (prev.x + curr.x) / 2
            val midY = (prev.y + curr.y) / 2
            linePath.quadraticTo(prev.x, prev.y, midX, midY)
            prev = curr
        }
        linePath.lineTo(prev.x, prev.y)

        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(segment.last().x, graphHeight)
            lineTo(segment.first().x, graphHeight)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.4f), Color.Transparent),
                startY = 0f,
                endY = graphHeight
            )
        )

        drawPath(
            path = linePath,
            color = color,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}