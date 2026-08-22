package com.defang.launcher.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.defang.launcher.di.AppModule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Runs every real Migration against a database built from the schema Room
 * exported for the version it starts from, then validates the result
 * against the version it lands on. This is the test that would have caught
 * issue #19: MIGRATION_4_5 originally created `session_extension` without
 * `id` NOT NULL, silently drifting from the entity's declared schema.
 */
@RunWith(RobolectricTestRunner::class)
class DefangDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        DefangDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate6To7_repairsSessionExtensionSchema() {
        helper.createDatabase(TEST_DB, 6).close()

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 7, true, AppModule.MIGRATION_6_7)
        migrated.close()
    }

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
