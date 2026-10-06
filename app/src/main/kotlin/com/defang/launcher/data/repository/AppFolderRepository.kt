package com.defang.launcher.data.repository

import com.defang.launcher.data.local.db.dao.AppFolderDao
import com.defang.launcher.data.local.db.entity.AppFolderEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppFolderRepository @Inject constructor(
    private val dao: AppFolderDao,
) {
    fun observeAll(): Flow<List<AppFolderEntity>> = dao.observeAll()

    suspend fun create(name: String): Long = dao.insert(AppFolderEntity(name = name))

    suspend fun rename(id: Long, name: String) = dao.rename(id, name)

    suspend fun setShowIndicator(id: Long, show: Boolean) = dao.setShowIndicator(id, show)

    suspend fun delete(id: Long) = dao.delete(id)
}
