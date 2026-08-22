package com.defang.launcher.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.defang.launcher.data.local.db.DefangDatabase
import com.defang.launcher.data.local.db.dao.AdaptiveGateStateDao
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
    // internal, not private: DefangDatabaseMigrationTest runs these directly
    // against real SQLite (via Robolectric) to catch schema drift like #19.
    internal val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE app_config ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
        }
    }

    // v3: user-configurable watched websites (URL gating)
    internal val MIGRATION_2_3 = object : Migration(2, 3) {
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
    internal val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE app_config ADD COLUMN customLabel TEXT")
            db.execSQL("ALTER TABLE app_config ADD COLUMN renamePromptDismissed INTEGER NOT NULL DEFAULT 0")
        }
    }

    // v5: dedicated table for gate-extension justifications, replacing the
    // "Extension: $reason" prefix jammed into sessions.intentDeclared — links
    // the reason back to both the session it extended and the one it started.
    internal val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS session_extension (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
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

    // v6: adaptive gate-threshold escalation state (issue #16, opt-in) — one row
    // per gate (app package or watched-URL pattern) tracking its current
    // escalation level and when it was last touched.
    internal val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS adaptive_gate_state (
                    gateKey TEXT NOT NULL PRIMARY KEY,
                    level INTEGER NOT NULL DEFAULT 0,
                    lastOpenAtMs INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
        }
    }

    // v7: rebuilds session_extension to repair installs that ran the
    // pre-fix MIGRATION_4_5 (shipped without `id` NOT NULL) — that table's
    // `id` column is stuck reporting notnull=false forever since Room only
    // applies a version transition once, which fails schema validation on
    // every launch (issue #19). Safe to run even if `id` is already correct.
    internal val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE session_extension_new (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    packageName TEXT NOT NULL,
                    extendedSessionId INTEGER NOT NULL,
                    newSessionId INTEGER NOT NULL,
                    reason TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO session_extension_new
                    (id, packageName, extendedSessionId, newSessionId, reason, timestamp)
                SELECT id, packageName, extendedSessionId, newSessionId, reason, timestamp
                FROM session_extension
                """.trimIndent()
            )
            db.execSQL("DROP TABLE session_extension")
            db.execSQL("ALTER TABLE session_extension_new RENAME TO session_extension")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DefangDatabase =
        Room.databaseBuilder(context, DefangDatabase::class.java, "defang.db")
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                MIGRATION_6_7,
            )
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

    @Provides
    fun provideAdaptiveGateStateDao(db: DefangDatabase): AdaptiveGateStateDao =
        db.adaptiveGateStateDao()
}
