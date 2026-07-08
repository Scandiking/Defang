package com.defang.launcher.data.repository

import com.defang.launcher.data.local.db.dao.SessionDao
import com.defang.launcher.data.local.db.entity.SessionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val dao: SessionDao,
) {
    suspend fun startSession(packageName: String, intentDeclared: String?): Long {
        val entity = SessionEntity(
            packageName = packageName,
            startTime = System.currentTimeMillis(),
            intentDeclared = intentDeclared,
        )
        return dao.insert(entity)
    }

    suspend fun endSession(sessionId: Long, extensionUsed: Boolean = false) {
        val existing = dao.getById(sessionId) ?: return
        dao.update(
            existing.copy(
                endTime = System.currentTimeMillis(),
                extensionUsed = extensionUsed,
            )
        )
    }

    suspend fun markExtensionUsed(sessionId: Long) {
        val existing = dao.getById(sessionId) ?: return
        dao.update(existing.copy(extensionUsed = true))
    }

    fun observeSessionsForApp(packageName: String): Flow<List<SessionEntity>> =
        dao.observeForApp(packageName)

    suspend fun getExtensionUsedToday(dayStartEpoch: Long): SessionEntity? =
        dao.getExtensionUsedToday(dayStartEpoch)
}
