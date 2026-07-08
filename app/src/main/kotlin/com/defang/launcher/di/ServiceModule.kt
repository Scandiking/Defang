package com.defang.launcher.di

// OverlayManager, TidbitSelector, and OfflinePromptSelector are @Singleton @Inject constructor
// so Hilt binds them automatically. This module is reserved for future Phase 2 bindings
// (e.g. AlarmManager wrappers for notification batching).

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule
