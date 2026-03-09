package app.grip_gains_companion.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
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
    restDurations: List<Double> = emptyList(), // NEW: Pass the real durations
    pColor: Color,
    sColor: Color = Color.Gray,
    connectGaps: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (times.isEmpty() || primary.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(color = onSurface, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    val restStyle = TextStyle(color = onSurfaceVariant, fontSize = 9.sp)
    val dividerColor = onSurface.copy(alpha = 0.2f)

    Canvas(modifier = modifier) {
        val validPrimary = primary.filter { !it.isNaN() }
        val maxP = (validPrimary.maxOrNull()?.toFloat() ?: 1f) * 1.2f
        val maxS = (secondary?.filter { !it.isNaN() }?.maxOrNull()?.toFloat() ?: 1f) * 1.2f

        val minTime = times.first().toFloat()
        val maxTime = times.last().toFloat()
        val timeRange = (maxTime - minTime).coerceAtLeast(1f)
        val graphHeight = size.height - 24.dp.toPx()

        var segmentStart = minTime
        var restCounter = 0
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

        for (i in 1 until times.size) {
            val prev = times[i-1].toFloat()
            val curr = times[i].toFloat()
            if (curr - prev >= 0.8f) {
                val midGapTime = (prev + curr) / 2f
                val gapX = ((midGapTime - minTime) / timeRange) * size.width

                drawLine(
                    color = dividerColor,
                    start = Offset(gapX, 0f),
                    end = Offset(gapX, size.height),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashEffect
                )

                val restTime = restDurations.getOrNull(restCounter)?.roundToInt() ?: 0
                val restLayout = textMeasurer.measure("${restTime}s Rest", restStyle)
                drawText(restLayout, onSurfaceVariant, Offset(gapX - restLayout.size.width / 2f, 4.dp.toPx()))

                val dur = prev - segmentStart
                val midX = (((segmentStart + prev) / 2f) - minTime) / timeRange * size.width
                val sLayout = textMeasurer.measure("${dur.roundToInt()}s", labelStyle)
                drawText(sLayout, onSurface, Offset(midX - sLayout.size.width / 2f, size.height - sLayout.size.height))

                segmentStart = curr; restCounter++
            }
        }

        // Final set label
        val finalDur = maxTime - segmentStart
        val finalMidX = (((segmentStart + maxTime) / 2f) - minTime) / timeRange * size.width
        val finalLayout = textMeasurer.measure("${finalDur.roundToInt()}s", labelStyle)
        drawText(finalLayout, color = onSurface, topLeft = Offset(finalMidX - finalLayout.size.width / 2f, size.height - finalLayout.size.height))

        secondary?.let { drawDataPath(times, it, minTime, timeRange, maxS, sColor, graphHeight, connectGaps) }
        drawDataPath(times, primary, minTime, timeRange, maxP, pColor, graphHeight, connectGaps)
    }
}

private fun DrawScope.drawDataPath(
    times: List<Double>, values: List<Double>, minX: Float, rangeX: Float, maxY: Float,
    color: Color, height: Float, connectGaps: Boolean
) {
    val segments = mutableListOf<MutableList<Offset>>()
    var current = mutableListOf<Offset>()

    for (i in times.indices) {
        val v = values[i]
        if (v.isNaN()) {
            if (!connectGaps && current.isNotEmpty()) {
                segments.add(current); current = mutableListOf()
            }
        } else {
            val x = ((times[i].toFloat() - minX) / rangeX) * size.width
            val y = height - ((v.toFloat() / maxY) * height)
            current.add(Offset(x, y.coerceIn(0f, height)))
        }
    }
    if (current.isNotEmpty()) segments.add(current)

    segments.forEach { seg ->
        if (seg.size < 2) return@forEach
        val path = Path().apply {
            moveTo(seg[0].x, seg[0].y)
            for (i in 1 until seg.size) lineTo(seg[i].x, seg[i].y)
        }

        val fillPath = Path().apply {
            addPath(path)
            lineTo(seg.last().x, height)
            lineTo(seg.first().x, height)
            close()
        }
        drawPath(fillPath, Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), Color.Transparent), endY = height))
        drawPath(path, color, style = Stroke(2.5.dp.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round))
    }
}