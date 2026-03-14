package app.grip_gains_companion.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RawSessionDao {
    @Query("SELECT * FROM raw_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<RawSessionEntity>>

    @Query("SELECT * FROM raw_sessions WHERE id = :id LIMIT 1")
    fun getSessionById(id: Long): Flow<RawSessionEntity?>

    @Query("DELETE FROM raw_sessions WHERE id = :id")
    fun deleteSessionById(id: Long) // No "suspend", no ": Int"

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(session: RawSessionEntity): Long

    @Update
    fun update(session: RawSessionEntity)

    @Delete
    fun delete(session: RawSessionEntity)
}