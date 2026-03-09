package app.grip_gains_companion.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class RawSessionRepository(private val dao: RawSessionDao) {
    val allSessions: Flow<List<RawSessionEntity>> = dao.getAllSessions()

    fun getSessionById(id: Long): Flow<RawSessionEntity?> {
        return dao.getSessionById(id)
    }

    suspend fun insert(session: RawSessionEntity) {
        withContext(Dispatchers.IO) {
            dao.insert(session)
        }
    }

    suspend fun update(session: RawSessionEntity) {
        withContext(Dispatchers.IO) {
            dao.update(session)
        }
    }

    suspend fun delete(session: RawSessionEntity) {
        withContext(Dispatchers.IO) {
            dao.delete(session)
        }
    }
}