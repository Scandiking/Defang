package com.defang.launcher.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.defang.launcher.data.local.db.DefangDatabase
import com.defang.launcher.data.local.db.dao.AppConfigDao
import com.defang.launcher.data.local.db.dao.SessionDao
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DefangDatabase =
        Room.databaseBuilder(context, DefangDatabase::class.java, "defang.db")
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideAppConfigDao(db: DefangDatabase): AppConfigDao = db.appConfigDao()

    @Provides
    fun provideSessionDao(db: DefangDatabase): SessionDao = db.sessionDao()
}
