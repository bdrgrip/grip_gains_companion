package app.grip_gains_companion.database

import kotlinx.coroutines.flow.Flow

class RawSessionRepository(private val dao: RawSessionDao) {
    val allSessions: Flow<List<RawSessionEntity>> = dao.getAllSessions()

    fun getSessionById(id: Long): Flow<RawSessionEntity?> {
        return dao.getSessionById(id)
    }

    suspend fun insert(session: RawSessionEntity) {
        dao.insert(session)
    }

    suspend fun update(session: RawSessionEntity) {
        dao.update(session)
    }

    suspend fun delete(session: RawSessionEntity) {
        dao.delete(session)
    }
}