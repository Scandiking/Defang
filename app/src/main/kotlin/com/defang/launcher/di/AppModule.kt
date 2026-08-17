package com.defang.launcher.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.defang.launcher.data.local.db.DefangDatabase
import com.defang.launcher.data.local.db.dao.AppConfigDao
import com.defang.launcher.data.local.db.dao.SessionDao
import com.defang.launcher.data.local.db.dao.SessionExtensionDao
import com.defang.launcher.data.local.db.dao.WatchedUrlDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // v2: per-app "hidden" flag — hidden apps only appear in drawer search
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE app_config ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
        }
    }

    // v3: user-configurable watched websites (URL gating)
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS watched_url (
                    pattern TEXT NOT NULL PRIMARY KEY,
                    label TEXT NOT NULL,
                    isAdult INTEGER NOT NULL DEFAULT 0,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    cooldownEndsAt INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
        }
    }

    // v4: user-chosen per-app display name override, plus the dismissed flag
    // for the one-time "two apps share this name" prompt
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE app_config ADD COLUMN customLabel TEXT")
            db.execSQL("ALTER TABLE app_config ADD COLUMN renamePromptDismissed INTEGER NOT NULL DEFAULT 0")
        }
    }

    // v5: dedicated table for gate-extension justifications, replacing the
    // "Extension: $reason" prefix jammed into sessions.intentDeclared — links
    // the reason back to both the session it extended and the one it started.
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS session_extension (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    packageName TEXT NOT NULL,
                    extendedSessionId INTEGER NOT NULL,
                    newSessionId INTEGER NOT NULL,
                    reason TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DefangDatabase =
        Room.databaseBuilder(context, DefangDatabase::class.java, "defang.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideAppConfigDao(db: DefangDatabase): AppConfigDao = db.appConfigDao()

    @Provides
    fun provideSessionDao(db: DefangDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideWatchedUrlDao(db: DefangDatabase): WatchedUrlDao = db.watchedUrlDao()

    @Provides
    fun provideSessionExtensionDao(db: DefangDatabase): SessionExtensionDao =
        db.sessionExtensionDao()
}
