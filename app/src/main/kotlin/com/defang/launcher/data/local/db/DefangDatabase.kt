package com.defang.launcher.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.defang.launcher.data.local.db.dao.AdaptiveGateStateDao
import com.defang.launcher.data.local.db.dao.AppConfigDao
import com.defang.launcher.data.local.db.dao.AppFolderDao
import com.defang.launcher.data.local.db.dao.SessionDao
import com.defang.launcher.data.local.db.dao.SessionExtensionDao
import com.defang.launcher.data.local.db.dao.WatchedUrlDao
import com.defang.launcher.data.local.db.entity.AdaptiveGateStateEntity
import com.defang.launcher.data.local.db.entity.AppConfigEntity
import com.defang.launcher.data.local.db.entity.AppFolderEntity
import com.defang.launcher.data.local.db.entity.SessionEntity
import com.defang.launcher.data.local.db.entity.SessionExtensionEntity
import com.defang.launcher.data.local.db.entity.WatchedUrlEntity

@Database(
    entities = [
        AppConfigEntity::class,
        SessionEntity::class,
        WatchedUrlEntity::class,
        SessionExtensionEntity::class,
        AdaptiveGateStateEntity::class,
        AppFolderEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
abstract class DefangDatabase : RoomDatabase() {
    abstract fun appConfigDao(): AppConfigDao
    abstract fun sessionDao(): SessionDao
    abstract fun watchedUrlDao(): WatchedUrlDao
    abstract fun sessionExtensionDao(): SessionExtensionDao
    abstract fun adaptiveGateStateDao(): AdaptiveGateStateDao
    abstract fun appFolderDao(): AppFolderDao
}
