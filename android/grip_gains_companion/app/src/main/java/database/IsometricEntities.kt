package app.grip_gains_companion.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.UUID

@Entity(tableName = "iso_sessions")
data class IsoSessionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val timestamp: Long,
    val gripperType: String,
    val scrapedGripper: String? = null,
    val scrapedSide: String? = null,
    val side: String,
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "iso_reps",
    foreignKeys = [
        ForeignKey(
            entity = IsoSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class IsoRepEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val timestamp: Long,
    val duration: Double,
    val targetWeight: Double?,
    val mean: Double,
    val median: Double,
    val stdDev: Double,
    val p1: Double,
    val p5: Double,
    val p10: Double,
    val q1: Double,
    val q3: Double,
    val p90: Double,
    val p95: Double,
    val p99: Double,
    val filterStartIndex: Int,
    val filterEndIndex: Int,
    val samples: List<Double>,
    val durationTarget: Int?
)