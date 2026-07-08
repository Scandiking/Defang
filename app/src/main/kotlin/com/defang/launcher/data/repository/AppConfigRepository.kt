package com.defang.launcher.data.repository

import com.defang.launcher.data.local.db.dao.AppConfigDao
import com.defang.launcher.data.local.db.entity.AppConfigEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppConfigRepository @Inject constructor(
    private val dao: AppConfigDao,
) {
    fun observeAll(): Flow<List<AppConfigEntity>> = dao.observeAll()

    fun observeWatched(): Flow<List<AppConfigEntity>> = dao.observeWatched()

    suspend fun getConfig(packageName: String): AppConfigEntity? = dao.getByPackage(packageName)

    suspend fun upsert(config: AppConfigEntity) = dao.upsert(config)

    suspend fun seedInstalledApps(configs: List<AppConfigEntity>) = dao.insertAll(configs)

    suspend fun setTier(packageName: String, tier: Int) = dao.setTier(packageName, tier)

    suspend fun setCooldown(packageName: String, endsAtEpoch: Long) =
        dao.setCooldown(packageName, endsAtEpoch)
}
