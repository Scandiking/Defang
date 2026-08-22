package com.defang.launcher.data.local.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.defang.launcher.di.AppModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Reproduces the real-world broken installs from issue #19: a database
 * whose `session_extension` table was created by the original, unpatched
 * MIGRATION_4_5 (no `id` NOT NULL). Room only ever applies a version
 * transition once per device, so those installs are stuck with this exact
 * table shape forever until a later migration rebuilds it.
 */
@RunWith(RobolectricTestRunner::class)
class SessionExtensionRepairMigrationTest {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun openBrokenDatabase() {
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .name("broken-session-extension.db")
            .callback(callback)
            .build()
        helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        db = helper.writableDatabase

        // The exact SQL the pre-fix MIGRATION_4_5 shipped with — no `NOT NULL`
        // on `id`, which SQLite then reports as notnull=0 in PRAGMA table_info.
        db.execSQL(
            """
            CREATE TABLE session_extension (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                packageName TEXT NOT NULL,
                extendedSessionId INTEGER NOT NULL,
                newSessionId INTEGER NOT NULL,
                reason TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "INSERT INTO session_extension " +
                "(packageName, extendedSessionId, newSessionId, reason, timestamp) " +
                "VALUES ('com.example.app', 1, 2, 'needed one more turn', 1000)"
        )
    }

    @After
    fun closeDatabase() {
        helper.close()
    }

    @Test
    fun migration6To7_fixesIdNotNullOnAlreadyBrokenTable() {
        assertFalse("id should start out notnull=false, matching real broken installs", idNotNull())

        AppModule.MIGRATION_6_7.migrate(db)

        assertTrue("id must be notnull=true after the repair migration", idNotNull())
        db.query("SELECT packageName, reason FROM session_extension").use { cursor ->
            assertTrue("existing row must survive the rebuild", cursor.moveToFirst())
            assertEquals("com.example.app", cursor.getString(0))
            assertEquals("needed one more turn", cursor.getString(1))
        }
    }

    private fun idNotNull(): Boolean =
        db.query("PRAGMA table_info(session_extension)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == "id") {
                    return cursor.getInt(notNullIndex) == 1
                }
            }
            error("id column not found")
        }
}
