package app.grip_gains_companion.service

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

data class SessionResult(
    val workoutScore: Double,
    val mechanicalWork: Double,
    val timeSeries: List<Double>,
    val tensionSeries: List<Double>,
    val magnitudeSeries: List<Double>,
    val powerSeries: List<Double>,
    val densitySeries: List<Double>,
    val workSeries: List<Double>
)

class AdaptiveAxisProjector {
    private var refX = 0.0; private var refY = 0.0; private var refZ = 0.0
    private val noiseFloor = 1.0; private val learningRate = 0.05

    fun projectTo1D(x: Float, y: Float, z: Float): Double {
        val dx = x.toDouble(); val dy = y.toDouble(); val dz = z.toDouble()
        val currentMag = sqrt(dx * dx + dy * dy + dz * dz)
        if (currentMag > noiseFloor) {
            var nx = dx / currentMag; var ny = dy / currentMag; var nz = dz / currentMag
            if (refX == 0.0 && refY == 0.0 && refZ == 0.0) { refX = nx; refY = ny; refZ = nz }
            else {
                if ((nx * refX) + (ny * refY) + (nz * refZ) < 0) { nx = -nx; ny = -ny; nz = -nz }
                refX = (refX * (1.0 - learningRate)) + (nx * learningRate)
                refY = (refY * (1.0 - learningRate)) + (ny * learningRate)
                refZ = (refZ * (1.0 - learningRate)) + (nz * learningRate)
                val refMag = sqrt(refX * refX + refY * refY + refZ * refZ)
                refX /= refMag; refY /= refMag; refZ /= refMag
            }
        }
        return (dx * refX) + (dy * refY) + (dz * refZ)
    }
}

class RawHoldBuffer() {
    var firstTimestamp: Long = 0L
    var lastTimestamp: Long = 0L
    val velocities = mutableListOf<Double>()
    val tensions = mutableListOf<Double>()
    val positions = mutableListOf<Double>()
    val timestamps = mutableListOf<Long>()
    var maxStroke: Double = 0.01
}

class RawSessionManager {
    private val projector = AdaptiveAxisProjector()
    private val leakFactor = 0.95
    private var velocity = 0.0
    private var position = 0.0

    private val holds = mutableListOf<RawHoldBuffer>()
    var isHoldActive = false
        private set

    fun startHold() {
        holds.add(RawHoldBuffer())
        isHoldActive = true
    }

    fun addSample(x: Float, y: Float, z: Float, tension: Double, timestampNanos: Long) {
        if (!isHoldActive || holds.isEmpty()) return
        val currentHold = holds.last()

        if (currentHold.lastTimestamp == 0L) {
            currentHold.firstTimestamp = timestampNanos
            currentHold.lastTimestamp = timestampNanos
            return
        }

        val dt = (timestampNanos - currentHold.lastTimestamp) / 1_000_000_000.0
        if (dt <= 0) return
        currentHold.lastTimestamp = timestampNanos

        val accel1D = projector.projectTo1D(x, y, z)
        velocity = (velocity + accel1D * dt) * leakFactor
        position = (position + velocity * dt) * leakFactor

        currentHold.velocities.add(abs(velocity))
        currentHold.tensions.add(tension)
        currentHold.positions.add(position)
        currentHold.timestamps.add(timestampNanos)
    }

    fun stopHold() {
        isHoldActive = false
        val currentHold = holds.lastOrNull() ?: return
        if (currentHold.positions.isNotEmpty()) {
            var cMin = currentHold.positions[0]
            for (p in currentHold.positions) {
                if (p < cMin) cMin = p
                currentHold.maxStroke = max(currentHold.maxStroke, abs(p - cMin))
            }
        }
    }

    fun finalizeSession(): SessionResult {
        if (holds.isEmpty()) return SessionResult(0.0, 0.0, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

        var totalWork = 0.0
        var totalScore = 0.0

        val timeSeries = mutableListOf<Double>()
        val tensionSeries = mutableListOf<Double>()
        val magnitudeSeries = mutableListOf<Double>()
        val powerSeries = mutableListOf<Double>()
        val densitySeries = mutableListOf<Double>()
        val workSeries = mutableListOf<Double>()

        var globalTimeOffset = 0.0
        var fatigueCarryover = 0.0
        var holdIndex = 1

        holds.forEachIndexed { index, hold ->
            if (hold.timestamps.isEmpty()) return@forEachIndexed

            val holdDuration = (hold.timestamps.last() - hold.timestamps.first()) / 1_000_000_000.0

            if (index > 0) {
                val restNanos = hold.firstTimestamp - holds[index-1].lastTimestamp
                val restSeconds = max(0.0, restNanos / 1_000_000_000.0)
                val prevDuration = (holds[index-1].lastTimestamp - holds[index-1].firstTimestamp) / 1_000_000_000.0
                fatigueCarryover = prevDuration * Math.exp(-0.04 * restSeconds)
            }

            val yieldModifier = Math.exp(-0.51 * (holdIndex - 1))
            val t0 = hold.timestamps.first()
            var holdScore = 0.0

            for (i in 1 until hold.velocities.size) {
                val dt = (hold.timestamps[i] - hold.timestamps[i-1]) / 1_000_000_000.0
                val t = (hold.timestamps[i] - t0) / 1_000_000_000.0
                val effectiveTime = t + fatigueCarryover

                val instPower = hold.tensions[i] * hold.velocities[i]
                val stepWork = (instPower / hold.maxStroke) * dt
                totalWork += stepWork

                val workScore = stepWork * (1.0 + 0.1 * effectiveTime)
                val tensionScore = hold.tensions[i] * dt * 0.005 * totalWork
                holdScore += (workScore + tensionScore) * yieldModifier

                val globalTime = globalTimeOffset + t
                timeSeries.add(globalTime)
                tensionSeries.add(hold.tensions[i])
                magnitudeSeries.add(hold.velocities[i])
                powerSeries.add(instPower)
                workSeries.add(totalWork)

                val currentIndex = powerSeries.size - 1
                val windowStartTime = globalTime - 3.0
                var powerSum = 0.0
                var powerCount = 0

                for (j in currentIndex downTo 0) {
                    if (timeSeries[j] >= windowStartTime) {
                        if (!powerSeries[j].isNaN()) {
                            powerSum += powerSeries[j]
                            powerCount++
                        }
                    } else {
                        break
                    }
                }

                val density = if (powerCount > 0) powerSum / powerCount else 0.0
                densitySeries.add(density)
            }

            totalScore += holdScore
            globalTimeOffset += holdDuration

            if (index < holds.size - 1) {
                val restNanos = holds[index+1].firstTimestamp - hold.lastTimestamp
                val restSeconds = max(0.0, restNanos / 1_000_000_000.0)
                globalTimeOffset += restSeconds

                timeSeries.add(globalTimeOffset)
                tensionSeries.add(Double.NaN)
                magnitudeSeries.add(Double.NaN)
                powerSeries.add(Double.NaN)
                densitySeries.add(Double.NaN)
                workSeries.add(totalWork)
            }
            holdIndex++
        }

        val decimationFactor = 10

        val dTimes = mutableListOf<Double>()
        val dTensions = mutableListOf<Double>()
        val dMags = mutableListOf<Double>()
        val dPowers = mutableListOf<Double>()
        val dDensities = mutableListOf<Double>()
        val dWorks = mutableListOf<Double>()

        var i = 0
        while (i < timeSeries.size) {
            if (timeSeries[i].isNaN() || tensionSeries[i].isNaN()) {
                dTimes.add(timeSeries[i])
                dTensions.add(Double.NaN)
                dMags.add(Double.NaN)
                dPowers.add(Double.NaN)
                dDensities.add(Double.NaN)
                dWorks.add(workSeries[i])
                i++
                continue
            }

            var count = 0
            var sumTension = 0.0
            var sumMag = 0.0
            var sumPower = 0.0
            var sumDensity = 0.0

            val chunkEnd = kotlin.math.min(i + decimationFactor, timeSeries.size)
            var lastValidIndex = i

            while (i < chunkEnd && !timeSeries[i].isNaN() && !tensionSeries[i].isNaN()) {
                sumTension += tensionSeries[i]
                sumMag += magnitudeSeries[i]
                sumPower += powerSeries[i]
                sumDensity += densitySeries[i]
                lastValidIndex = i
                count++
                i++
            }

            if (count > 0) {
                dTimes.add(timeSeries[lastValidIndex])
                dWorks.add(workSeries[lastValidIndex])

                dTensions.add(sumTension / count)
                dMags.add(sumMag / count)
                dPowers.add(sumPower / count)
                dDensities.add(sumDensity / count)
            }
        }

        return SessionResult(totalWork, totalScore, dTimes, dTensions, dMags, dPowers, dDensities, dWorks)
    }
}