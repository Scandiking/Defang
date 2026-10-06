package com.defang.launcher.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-made drawer folder (issue #41, opt-in). Apps point at it through
 * [AppConfigEntity.folderId]; the folder itself only carries its name and
 * whether its drawer row gets a trailing arrow — off by default, so a folder
 * reads like any other text row unless the user marks it per folder.
 */
@Entity(tableName = "app_folder")
data class AppFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val showIndicator: Boolean = false,
)
