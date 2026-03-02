package app.grip_gains_companion.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class RawSessionRepository(private val dao: RawSessionDao) {
    val allSessions: Flow<List<RawSessionEntity>> = dao.getAllSessions()

    suspend fun insert(session: RawSessionEntity): Long = withContext(Dispatchers.IO) {
        dao.insertSession(session)
    }

    suspend fun update(session: RawSessionEntity): Int = withContext(Dispatchers.IO) {
        dao.updateSession(session)
    }

    suspend fun delete(session: RawSessionEntity): Int = withContext(Dispatchers.IO) {
        dao.deleteSession(session)
    }

    suspend fun clearHistory(): Int = withContext(Dispatchers.IO) {
        dao.clearAllHistory()
    }
}