package app.grip_gains_companion.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import androidx.room.Delete

data class IsoSessionWithReps(
    @Embedded val session: IsoSessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val reps: List<IsoRepEntity>
)

@Dao
interface IsoSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSession(session: IsoSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRep(rep: IsoRepEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertReps(reps: List<IsoRepEntity>)

    @Transaction
    @Query("SELECT * FROM iso_sessions WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getAllSessionsWithReps(): Flow<List<IsoSessionWithReps>>

    // ADD THIS FUNCTION
    @Query("SELECT * FROM iso_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<IsoSessionEntity>>

    // Add this one too if you ever need to fetch reps for a specific session
    @Query("SELECT * FROM iso_reps WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getRepsForSession(sessionId: String): Flow<List<IsoRepEntity>>

    @Transaction
    @Query("SELECT * FROM iso_sessions WHERE id = :sessionId LIMIT 1")
    fun getSessionWithRepsById(sessionId: String): Flow<IsoSessionWithReps?>

    @Query("UPDATE iso_sessions SET isDeleted = 1 WHERE id = :sessionId")
    fun softDeleteSession(sessionId: String)

    @Query("SELECT * FROM iso_sessions WHERE id = :sessionId LIMIT 1")
    fun getSessionById(sessionId: String): Flow<IsoSessionEntity?>

    @Query("DELETE FROM iso_sessions WHERE id = :sessionId")
    fun deleteSessionById(sessionId: String)

    @Query("DELETE FROM iso_sessions WHERE isDeleted = 1")
    fun purgeDeletedSessions()
}