package com.defang.launcher.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.defang.launcher.data.local.db.entity.AppFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppFolderDao {

    @Query("SELECT * FROM app_folder ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<AppFolderEntity>>

    @Insert
    suspend fun insert(folder: AppFolderEntity): Long

    @Query("UPDATE app_folder SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE app_folder SET showIndicator = :show WHERE id = :id")
    suspend fun setShowIndicator(id: Long, show: Boolean)

    @Query("DELETE FROM app_folder WHERE id = :id")
    suspend fun deleteRow(id: Long)

    @Query("UPDATE app_config SET folderId = NULL WHERE folderId = :id")
    suspend fun releaseApps(id: Long)

    /** Deleting a folder never touches its apps — they fall back to the top level. */
    @Transaction
    suspend fun delete(id: Long) {
        releaseApps(id)
        deleteRow(id)
    }
}
