package com.defang.launcher.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.defang.launcher.data.local.db.entity.WatchedUrlEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchedUrlDao {

    @Query("SELECT * FROM watched_url ORDER BY label ASC")
    fun observeAll(): Flow<List<WatchedUrlEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchedUrlEntity)

    @Query("DELETE FROM watched_url WHERE pattern = :pattern")
    suspend fun delete(pattern: String)

    @Query("UPDATE watched_url SET isAdult = :isAdult WHERE pattern = :pattern")
    suspend fun setAdult(pattern: String, isAdult: Boolean)

    @Query("UPDATE watched_url SET enabled = :enabled WHERE pattern = :pattern")
    suspend fun setEnabled(pattern: String, enabled: Boolean)

    @Query("UPDATE watched_url SET cooldownEndsAt = :endsAt WHERE pattern = :pattern")
    suspend fun setCooldown(pattern: String, endsAt: Long)
}
