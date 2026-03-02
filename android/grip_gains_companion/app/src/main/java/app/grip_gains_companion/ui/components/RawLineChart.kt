package app.grip_gains_companion.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun RawLineChart(
    times: List<Double>,
    primary: List<Double>,
    secondary: List<Double>?,
    pColor: Color,
    sColor: Color = Color.Gray
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(Color.Black.copy(alpha = 0.2f))
    ) {
        if (times.isEmpty()) return@Canvas
        val maxX = times.last().toFloat()
        val maxP = (primary.filter { !it.isNaN() }.maxOrNull() ?: 1.0).toFloat()
        val maxS = (secondary?.filter { !it.isNaN() }?.maxOrNull() ?: 1.0).toFloat()

        // 1. Detect rest intervals by finding time jumps > 0.5 seconds
        val gapIndices = mutableListOf<Int>()
        for (i in 1 until times.size) {
            if (times[i] - times[i - 1] > 0.5) {
                gapIndices.add(i)
            }
        }

        // 2. Draw highly visible dotted vertical lines at those exact timestamps
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)

        gapIndices.forEach { i ->
            val x = (times[i].toFloat() / maxX) * size.width
            drawLine(
                color = Color.White.copy(alpha = 0.6f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 2.dp.toPx(), // Converted to DP so it's actually visible!
                pathEffect = dashEffect
            )
        }

        // 3. Draw the metric lines
        drawDataPath(times, primary, maxX, maxP, pColor)
        secondary?.let { drawDataPath(times, it, maxX, maxS, sColor) }
    }
}

private fun DrawScope.drawDataPath(times: List<Double>, values: List<Double>, maxX: Float, maxY: Float, color: Color) {
    val path = Path()
    val width = size.width
    val height = size.height
    var isNextMove = true

    times.forEachIndexed { i, time ->
        val v = values[i]

        // Lift the pen if the value is NaN or if we just crossed a rest interval gap
        val isTimeGap = i > 0 && (times[i] - times[i-1] > 0.5)

        if (v.isNaN() || isTimeGap) {
            isNextMove = true
        } else {
            val x = (time.toFloat() / maxX) * width
            val y = (height - ((v.toFloat() / maxY) * height)).coerceAtMost(height - 2f)

            if (isNextMove) {
                path.moveTo(x, y)
                isNextMove = false
            } else {
                path.lineTo(x, y)
            }
        }
    }
    drawPath(path = path, color = color, style = Stroke(width = 2.dp.toPx()))
}