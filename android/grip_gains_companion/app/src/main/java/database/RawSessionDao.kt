package app.grip_gains_companion.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@JvmSuppressWildcards
@Dao
interface RawSessionDao {
    @Query("SELECT * FROM raw_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<RawSessionEntity>>

    @Query("SELECT * FROM raw_sessions WHERE id = :id")
    fun getSessionById(id: Long): Flow<RawSessionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: RawSessionEntity): Long

    @Update
    suspend fun update(session: RawSessionEntity): Int

    @Delete
    suspend fun delete(session: RawSessionEntity): Int
}