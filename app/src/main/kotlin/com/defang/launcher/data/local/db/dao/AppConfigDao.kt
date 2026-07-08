package com.defang.launcher.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.defang.launcher.data.local.db.entity.AppConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppConfigDao {

    @Query("SELECT * FROM app_config ORDER BY appLabel ASC")
    fun observeAll(): Flow<List<AppConfigEntity>>

    @Query("SELECT * FROM app_config WHERE packageName = :pkg LIMIT 1")
    suspend fun getByPackage(pkg: String): AppConfigEntity?

    @Query("SELECT * FROM app_config WHERE tier = 1 ORDER BY appLabel ASC")
    fun observeWatched(): Flow<List<AppConfigEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: AppConfigEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(configs: List<AppConfigEntity>)

    @Update
    suspend fun update(config: AppConfigEntity)

    @Query("UPDATE app_config SET cooldownEndsAt = :endsAt WHERE packageName = :pkg")
    suspend fun setCooldown(pkg: String, endsAt: Long)

    @Query("UPDATE app_config SET tier = :tier WHERE packageName = :pkg")
    suspend fun setTier(pkg: String, tier: Int)

    /** Apply new global defaults to every watched app at once. */
    @Query("""
        UPDATE app_config
        SET gateDelaySeconds = :gateDelay,
            sessionLimitMinutes = :sessionLimit,
            cooldownMinutes = :cooldown
        WHERE tier = 1
    """)
    suspend fun applyDefaultsToAllWatched(gateDelay: Int, sessionLimit: Int, cooldown: Int)
}
