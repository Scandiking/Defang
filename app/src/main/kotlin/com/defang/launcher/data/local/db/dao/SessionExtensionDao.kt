package com.defang.launcher.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.defang.launcher.data.local.db.entity.SessionExtensionEntity

@Dao
interface SessionExtensionDao {

    @Insert
    suspend fun insert(extension: SessionExtensionEntity): Long

    @Query("SELECT * FROM session_extension WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getSince(since: Long): List<SessionExtensionEntity>

    @Query("SELECT * FROM session_extension")
    suspend fun getAll(): List<SessionExtensionEntity>

    @Query("DELETE FROM session_extension WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
