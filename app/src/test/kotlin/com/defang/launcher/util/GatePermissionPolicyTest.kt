package com.defang.launcher.util

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.defang.launcher.data.local.db.DefangDatabase
import com.defang.launcher.data.local.db.entity.AppConfigEntity
import com.defang.launcher.data.local.db.entity.WatchedUrlEntity
import com.defang.launcher.data.repository.AppConfigRepository
import com.defang.launcher.data.repository.WatchedUrlRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** #89: gate permissions are only asked for when something is actually watched. */
@RunWith(RobolectricTestRunner::class)
class GatePermissionPolicyTest {

    private lateinit var db: DefangDatabase
    private lateinit var policy: GatePermissionPolicy

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DefangDatabase::class.java,
        ).allowMainThreadQueries().build()
        policy = GatePermissionPolicy(
            AppConfigRepository(db.appConfigDao()),
            WatchedUrlRepository(db.watchedUrlDao()),
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun emptyDatabase_doesNotAsk() = runBlocking {
        assertFalse(policy.needsGatePermissions())
    }

    @Test
    fun onlyUtilityApps_doesNotAsk() = runBlocking {
        db.appConfigDao().upsert(AppConfigEntity("a.b", "A", tier = 0))
        assertFalse(policy.needsGatePermissions())
    }

    @Test
    fun watchedApp_asks() = runBlocking {
        db.appConfigDao().upsert(AppConfigEntity("a.b", "A", tier = 0))
        db.appConfigDao().upsert(AppConfigEntity("c.d", "C", tier = 1))
        assertTrue(policy.needsGatePermissions())
    }

    @Test
    fun disabledWatchedSite_doesNotAsk() = runBlocking {
        db.watchedUrlDao().upsert(WatchedUrlEntity("example.com", "Example", enabled = false))
        assertFalse(policy.needsGatePermissions())
    }

    @Test
    fun enabledWatchedSite_asks() = runBlocking {
        db.watchedUrlDao().upsert(WatchedUrlEntity("example.com", "Example", enabled = true))
        assertTrue(policy.needsGatePermissions())
    }
}
