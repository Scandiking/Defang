package com.defang.launcher.di

import android.content.Context
import androidx.room.Room
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DefangDatabase =
        Room.databaseBuilder(context, DefangDatabase::class.java, "defang.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideAppConfigDao(db: DefangDatabase): AppConfigDao = db.appConfigDao()

    @Provides
    fun provideSessionDao(db: DefangDatabase): SessionDao = db.sessionDao()
}
