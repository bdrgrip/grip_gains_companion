package app.grip_gains_companion.service

import app.grip_gains_companion.database.IsoRepEntity
import java.util.UUID
import kotlin.math.pow
import kotlin.math.sqrt

class IsoSessionManager {
    val completedReps = mutableListOf<IsoRepEntity>()

    var isRepActive = false
        private set

    private var currentSamples = mutableListOf<Double>()
    private var repStartTime = 0L

    fun startRep() {
        isRepActive = true
        currentSamples.clear()
        repStartTime = System.currentTimeMillis()
    }

    fun addSample(force: Double) {
        if (isRepActive) currentSamples.add(force)
    }

    fun endRep(targetWeight: Double? = null, targetDuration: Int? = null) {
        if (!isRepActive) return
        isRepActive = false

        val duration = (System.currentTimeMillis() - repStartTime) / 1000.0
        val sortedSamples = currentSamples.sorted()

        val hasData = currentSamples.isNotEmpty()

        val mean = if (hasData) currentSamples.average() else 0.0
        val median = if (hasData) percentile(sortedSamples, 50.0) else 0.0
        val variance = if (hasData) currentSamples.map { (it - mean).pow(2) }.average() else 0.0
        val stdDev = if (variance.isNaN()) 0.0 else sqrt(variance)

        val rep = IsoRepEntity(
            id = UUID.randomUUID().toString(),
            sessionId = "",
            timestamp = repStartTime,
            duration = duration,
            targetWeight = targetWeight,
            mean = mean,
            median = median,
            stdDev = stdDev,
            durationTarget = targetDuration,
            p1 = if (hasData) percentile(sortedSamples, 1.0) else 0.0,
            p5 = if (hasData) percentile(sortedSamples, 5.0) else 0.0,
            p10 = if (hasData) percentile(sortedSamples, 10.0) else 0.0,
            q1 = if (hasData) percentile(sortedSamples, 25.0) else 0.0,
            q3 = if (hasData) percentile(sortedSamples, 75.0) else 0.0,
            p90 = if (hasData) percentile(sortedSamples, 90.0) else 0.0,
            p95 = if (hasData) percentile(sortedSamples, 95.0) else 0.0,
            p99 = if (hasData) percentile(sortedSamples, 99.0) else 0.0,
            filterStartIndex = 0,
            filterEndIndex = if (hasData) currentSamples.size - 1 else 0,
            samples = currentSamples.toList()
        )

        completedReps.add(rep)
        currentSamples.clear()
    }

    fun clearSession() {
        completedReps.clear()
        currentSamples.clear()
        isRepActive = false
    }

    private fun percentile(sortedData: List<Double>, percentile: Double): Double {
        if (sortedData.isEmpty()) return 0.0
        val index = (percentile / 100.0) * (sortedData.size - 1)
        val lower = index.toInt()
        val upper = lower + 1
        val weight = index - lower
        if (upper >= sortedData.size) return sortedData[lower]
        return sortedData[lower] * (1.0 - weight) + sortedData[upper] * weight
    }
}