package app.grip_gains_companion.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RawSessionDao {
    @Insert
    fun insertSession(session: RawSessionEntity): Long

    @Update
    fun updateSession(session: RawSessionEntity): Int

    @Delete
    fun deleteSession(session: RawSessionEntity): Int

    @Query("SELECT * FROM raw_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<RawSessionEntity>>

    @Query("DELETE FROM raw_sessions")
    fun clearAllHistory(): Int
}