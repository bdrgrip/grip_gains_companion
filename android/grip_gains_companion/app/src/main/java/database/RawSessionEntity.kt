package app.grip_gains_companion.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "raw_sessions")
data class RawSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val targetMuscle: String,
    val bodySide: String,
    val mechanicalWork: Double,
    val workoutScore: Double = 0.0,
    val targetWeight: Double,
    val durationSeconds: Double,

    val timeSeries: List<Double>,
    val tensionSeries: List<Double>,
    val magnitudeSeries: List<Double>,
    val powerSeries: List<Double>,
    val densitySeries: List<Double>,
    val workSeries: List<Double>,
    val repTimestamps: List<Double> = emptyList(),
    val averageRepInterval: Double = 0.0,
    val restDurations: List<Double> = emptyList()
)

fun RawSessionEntity.calculateRetroactiveScore(): Double {
    var totalScore = 0.0
    var holdIndex = 1
    var fatigueCarryover = 0.0
    var currentHoldStartTime = -1.0
    var previousHoldEndTime = 0.0
    var previousHoldDuration = 0.0

    val times = this.timeSeries
    val tensions = this.tensionSeries
    val works = this.workSeries

    if (times.isEmpty() || times.size < 2) return 0.0

    var i = 1
    while (i < times.size) {
        // Detect rest intervals via NaN markers
        if (tensions[i].isNaN()) {
            if (currentHoldStartTime >= 0) {
                previousHoldEndTime = times[i - 1]
                previousHoldDuration = previousHoldEndTime - currentHoldStartTime
            }
            holdIndex++

            // Fast-forward through the rest gap
            while (i < times.size && tensions[i].isNaN()) { i++ }

            if (i < times.size) {
                val restSeconds = times[i] - previousHoldEndTime
                fatigueCarryover = previousHoldDuration * Math.exp(-0.04 * restSeconds)
                currentHoldStartTime = times[i]
            }
            continue
        }

        if (currentHoldStartTime < 0) currentHoldStartTime = times[i - 1]

        val dt = times[i] - times[i - 1]

        // Ensure we aren't calculating a massive dt across a gap
        if (dt > 0 && !tensions[i - 1].isNaN()) {
            val yieldModifier = Math.exp(-0.51 * (holdIndex - 1))
            val t = times[i] - currentHoldStartTime
            val effectiveTime = t + fatigueCarryover

            val stepWork = works[i] - works[i - 1]
            val workScore = stepWork * (1.0 + 0.1 * effectiveTime)
            val tensionScore = tensions[i] * dt * 0.005 * works[i]

            totalScore += (workScore + tensionScore) * yieldModifier
        }
        i++
    }
    return totalScore
}