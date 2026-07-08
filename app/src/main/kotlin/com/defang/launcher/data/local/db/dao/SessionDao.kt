package com.defang.launcher.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.defang.launcher.data.local.db.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE packageName = :pkg ORDER BY startTime DESC LIMIT :limit")
    fun observeForApp(pkg: String, limit: Int = 50): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE startTime >= :since ORDER BY startTime DESC")
    suspend fun getSince(since: Long): List<SessionEntity>

    /**
     * Returns sessions where extensionUsed = 1 and the session started on the same calendar day
     * as [dayStartEpoch]. Used to enforce the one-extension-per-day rule.
     */
    @Query(
        """
        SELECT * FROM sessions
        WHERE extensionUsed = 1
          AND startTime >= :dayStartEpoch
        LIMIT 1
    """
    )
    suspend fun getExtensionUsedToday(dayStartEpoch: Long): SessionEntity?
}
