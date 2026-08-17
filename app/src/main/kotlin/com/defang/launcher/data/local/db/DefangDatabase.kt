package com.defang.launcher.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.defang.launcher.data.local.db.dao.AppConfigDao
import com.defang.launcher.data.local.db.dao.SessionDao
import com.defang.launcher.data.local.db.dao.WatchedUrlDao
import com.defang.launcher.data.local.db.entity.AppConfigEntity
import com.defang.launcher.data.local.db.entity.SessionEntity
import com.defang.launcher.data.local.db.entity.WatchedUrlEntity

@Database(
    entities = [AppConfigEntity::class, SessionEntity::class, WatchedUrlEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class DefangDatabase : RoomDatabase() {
    abstract fun appConfigDao(): AppConfigDao
    abstract fun sessionDao(): SessionDao
    abstract fun watchedUrlDao(): WatchedUrlDao
}
