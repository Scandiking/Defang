package com.defang.launcher.data.repository

import com.defang.launcher.data.local.db.dao.WatchedUrlDao
import com.defang.launcher.domain.model.WatchedUrl
import com.defang.launcher.domain.model.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchedUrlRepository @Inject constructor(
    private val dao: WatchedUrlDao,
) {
    fun observeAll(): Flow<List<WatchedUrl>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun upsert(url: WatchedUrl) = dao.upsert(url.toEntity())

    suspend fun delete(pattern: String) = dao.delete(pattern)

    suspend fun setAdult(pattern: String, isAdult: Boolean) = dao.setAdult(pattern, isAdult)

    suspend fun setEnabled(pattern: String, enabled: Boolean) = dao.setEnabled(pattern, enabled)

    suspend fun setCooldown(pattern: String, endsAtEpoch: Long) =
        dao.setCooldown(pattern, endsAtEpoch)
}
