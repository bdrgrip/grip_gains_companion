package app.grip_gains_companion.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class SessionType { ISOTONIC, ISOMETRIC }

class SessionRepository(
    private val rawSessionDao: RawSessionDao,
    private val isoSessionDao: IsoSessionDao
) {
    suspend fun insertRaw(session: RawSessionEntity) {
        withContext(Dispatchers.IO) {
            rawSessionDao.insert(session)
        }
    }

    fun getAllRawSessions(): Flow<List<RawSessionEntity>> {
        return rawSessionDao.getAllSessions()
    }

    suspend fun insertIsoSession(session: IsoSessionEntity) {
        withContext(Dispatchers.IO) {
            isoSessionDao.insertSession(session)
        }
    }

    suspend fun insertIsoReps(reps: List<IsoRepEntity>) {
        withContext(Dispatchers.IO) {
            isoSessionDao.insertReps(reps)
        }
    }

    fun getAllIsoSessions(): Flow<List<IsoSessionEntity>> {
        return isoSessionDao.getAllSessions()
    }

    fun getIsoSessionById(id: String): Flow<IsoSessionEntity?> {
        return isoSessionDao.getSessionById(id)
    }

    fun getIsoRepsForSession(id: String): Flow<List<IsoRepEntity>> {
        return isoSessionDao.getRepsForSession(id)
    }

    suspend fun deleteIsoSession(session: IsoSessionEntity) {
        withContext(Dispatchers.IO) {
            isoSessionDao.deleteSessionById(session.id)
        }
    }
}