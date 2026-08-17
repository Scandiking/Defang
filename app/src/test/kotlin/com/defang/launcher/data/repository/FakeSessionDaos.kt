package com.defang.launcher.data.repository

import com.defang.launcher.data.local.db.dao.SessionDao
import com.defang.launcher.data.local.db.dao.SessionExtensionDao
import com.defang.launcher.data.local.db.entity.SessionEntity
import com.defang.launcher.data.local.db.entity.SessionExtensionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Simple in-memory fakes for the session DAOs, matching this codebase's
 * convention of plain data classes / hand-written test doubles rather than a
 * mocking framework for straightforward CRUD interfaces.
 */
class FakeSessionDao : SessionDao {
    val rows = mutableListOf<SessionEntity>()
    private var nextId = 1L

    override suspend fun insert(session: SessionEntity): Long {
        val id = if (session.id != 0L) session.id else nextId++
        rows.removeAll { it.id == id }
        rows += session.copy(id = id)
        return id
    }

    override suspend fun update(session: SessionEntity) {
        val idx = rows.indexOfFirst { it.id == session.id }
        if (idx >= 0) rows[idx] = session
    }

    override suspend fun getById(id: Long): SessionEntity? = rows.find { it.id == id }

    override fun observeForApp(pkg: String, limit: Int): Flow<List<SessionEntity>> =
        flowOf(rows.filter { it.packageName == pkg }.sortedByDescending { it.startTime }.take(limit))

    override suspend fun getSince(since: Long): List<SessionEntity> =
        rows.filter { it.startTime >= since }.sortedByDescending { it.startTime }

    override suspend fun getAll(): List<SessionEntity> =
        rows.sortedByDescending { it.startTime }

    override suspend fun countSince(since: Long): Int =
        rows.count { it.startTime >= since }

    override suspend fun getExtensionUsedToday(dayStartEpoch: Long): SessionEntity? =
        rows.firstOrNull { it.extensionUsed && it.startTime >= dayStartEpoch }

    override suspend fun deleteOlderThan(cutoff: Long) {
        rows.removeAll { it.startTime < cutoff }
    }

    override suspend fun getLegacyExtensionsSince(since: Long): List<SessionEntity> =
        rows.filter { it.intentDeclared?.startsWith("Extension: ") == true && it.startTime >= since }
            .sortedByDescending { it.startTime }
}

class FakeSessionExtensionDao : SessionExtensionDao {
    val rows = mutableListOf<SessionExtensionEntity>()
    private var nextId = 1L

    override suspend fun insert(extension: SessionExtensionEntity): Long {
        val id = if (extension.id != 0L) extension.id else nextId++
        rows += extension.copy(id = id)
        return id
    }

    override suspend fun getSince(since: Long): List<SessionExtensionEntity> =
        rows.filter { it.timestamp >= since }.sortedByDescending { it.timestamp }

    override suspend fun getAll(): List<SessionExtensionEntity> = rows.toList()

    override suspend fun deleteOlderThan(cutoff: Long) {
        rows.removeAll { it.timestamp < cutoff }
    }
}
