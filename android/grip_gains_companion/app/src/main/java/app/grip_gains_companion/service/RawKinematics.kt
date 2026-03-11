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
    val workSeries: List<Double>,
    val repTimestamps: List<Double>,
    val averageRepInterval: Double,
    val timeUnderTension: Double,
    val restDurations: List<Double>
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
        if (holds.isEmpty()) return SessionResult(0.0, 0.0, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), 0.0, 0.0, emptyList())

        var totalWork = 0.0
        var totalScore = 0.0
        var totalTUT = 0.0
        val restDurations = mutableListOf<Double>()
        val timeSeries = mutableListOf<Double>()
        val tensionSeries = mutableListOf<Double>()
        val rawMagnitudeSeries = mutableListOf<Double>()
        val powerSeries = mutableListOf<Double>()
        val densitySeries = mutableListOf<Double>()
        val workSeries = mutableListOf<Double>()

        var globalTimeOffset = 0.0
        var fatigueCarryover = 0.0

        holds.forEachIndexed { index, hold ->
            if (hold.timestamps.isEmpty()) return@forEachIndexed
            val holdDuration = (hold.timestamps.last() - hold.timestamps.first()) / 1_000_000_000.0
            totalTUT += holdDuration

            if (index > 0) {
                val restNanos = hold.firstTimestamp - holds[index-1].lastTimestamp
                val restSeconds = max(0.0, restNanos / 1_000_000_000.0)
                restDurations.add(restSeconds)
                fatigueCarryover = ((holds[index-1].lastTimestamp - holds[index-1].firstTimestamp) / 1_000_000_000.0) * Math.exp(-0.04 * restSeconds)
            }

            val yieldModifier = Math.exp(-0.51 * index)
            val t0 = hold.timestamps.first()

            for (i in 0 until hold.velocities.size) {
                val dt = if (i == 0) 0.0 else (hold.timestamps[i] - hold.timestamps[i-1]) / 1_000_000_000.0
                val t = (hold.timestamps[i] - t0) / 1_000_000_000.0
                val instPower = hold.tensions[i] * hold.velocities[i]
                val stepWork = (instPower / hold.maxStroke) * dt
                totalWork += stepWork
                totalScore += (stepWork * (1.0 + 0.1 * (t + fatigueCarryover)) + hold.tensions[i] * dt * 0.005 * totalWork) * yieldModifier

                val globalTime = globalTimeOffset + t
                timeSeries.add(globalTime)
                tensionSeries.add(hold.tensions[i])
                rawMagnitudeSeries.add(hold.velocities[i])
                powerSeries.add(instPower)
                workSeries.add(totalWork)

                val windowStartTime = globalTime - 3.0
                var pSum = 0.0; var pCount = 0
                for (j in powerSeries.size - 1 downTo 0) {
                    if (timeSeries[j] >= windowStartTime) {
                        if (!powerSeries[j].isNaN()) { pSum += powerSeries[j]; pCount++ }
                    } else break
                }
                densitySeries.add(if (pCount > 0) pSum / pCount else 0.0)
            }
            globalTimeOffset += holdDuration
            if (index < holds.size - 1) {
                globalTimeOffset += 1.0
                timeSeries.add(globalTimeOffset)
                tensionSeries.add(Double.NaN); rawMagnitudeSeries.add(Double.NaN)
                powerSeries.add(Double.NaN); densitySeries.add(Double.NaN)
                workSeries.add(totalWork)
            }
        }

        // --- STATISTICAL CLAMPING FOR EXTREME OUTLIERS ---
        val validMags = rawMagnitudeSeries.filter { !it.isNaN() }.sorted()
        val clampedMagnitudeSeries = mutableListOf<Double>()

        if (validMags.isNotEmpty()) {
            // Find the 75th percentile. We use this as a baseline to define "normal" maximums.
            val q3Index = (validMags.size * 0.75).toInt()
            val q3 = validMags[q3Index]

            // Allow spikes to be 2.5x larger than the 75th percentile before we consider them "equipment fumbles"
            val ceiling = q3 * 2.5

            rawMagnitudeSeries.forEach { mag ->
                if (mag.isNaN()) {
                    clampedMagnitudeSeries.add(Double.NaN)
                } else {
                    // Clamp extreme spikes to the logical ceiling!
                    clampedMagnitudeSeries.add(if (mag > ceiling) ceiling else mag)
                }
            }
        }

        val validClamped = clampedMagnitudeSeries.filter { !it.isNaN() }
        val threshold = (if (validClamped.isNotEmpty()) validClamped.maxOrNull() ?: 0.0 else 0.0) * 0.20
        val rawPeaks = mutableListOf<Double>()
        var lastPeakTime = -Double.MAX_VALUE

        for (i in 1 until clampedMagnitudeSeries.size - 1) {
            val m = clampedMagnitudeSeries[i]
            if (m.isNaN()) continue
            if (m >= threshold && m > (clampedMagnitudeSeries[i-1].takeIf { !it.isNaN() } ?: 0.0) && m > (clampedMagnitudeSeries[i+1].takeIf { !it.isNaN() } ?: 0.0)) {
                if (timeSeries[i] - lastPeakTime > 0.25 && !(clampedMagnitudeSeries.getOrNull(i+1)?.isNaN() ?: true)) {
                    rawPeaks.add(timeSeries[i]); lastPeakTime = timeSeries[i]
                }
            }
        }
        val repTimestamps = rawPeaks.filterIndexed { idx, _ -> idx % 2 == 0 }
        val avgInterval = if (repTimestamps.size > 1) repTimestamps.zipWithNext { a, b -> b - a }.average() else 0.0

        val decimation = 10
        val dTimes = mutableListOf<Double>(); val dTensions = mutableListOf<Double>()
        val dMags = mutableListOf<Double>(); val dPowers = mutableListOf<Double>()
        val dDensities = mutableListOf<Double>(); val dWorks = mutableListOf<Double>()
        var k = 0
        while (k < timeSeries.size) {
            if (timeSeries[k].isNaN()) {
                dTimes.add(timeSeries[k]); dTensions.add(Double.NaN); dMags.add(Double.NaN)
                dPowers.add(Double.NaN); dDensities.add(Double.NaN); dWorks.add(workSeries[k])
                k++; continue
            }
            var count = 0; var sT = 0.0; var sM = 0.0; var sP = 0.0; var sD = 0.0
            val end = kotlin.math.min(k + decimation, timeSeries.size)
            var lastV = k
            while (k < end && !timeSeries[k].isNaN()) {
                sT += tensionSeries[k]; sM += clampedMagnitudeSeries[k]; sP += powerSeries[k]
                sD += densitySeries[k]; lastV = k; count++; k++
            }
            if (count > 0) {
                dTimes.add(timeSeries[lastV]); dWorks.add(workSeries[lastV])
                dTensions.add(sT / count); dMags.add(sM / count)
                dPowers.add(sP / count); dDensities.add(sD / count)
            }
        }

        return SessionResult(totalScore, totalWork, dTimes, dTensions, dMags, dPowers, dDensities, dWorks, repTimestamps, avgInterval, totalTUT, restDurations)
    }
}