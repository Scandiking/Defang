package com.defang.launcher.data.repository

import com.defang.launcher.data.local.datastore.PreferencesDataStore
import com.defang.launcher.data.local.db.entity.SessionEntity
import com.defang.launcher.data.local.db.entity.SessionExtensionEntity
import com.defang.launcher.domain.model.RetentionLevel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRepositoryTest {

    private val sessionDao = FakeSessionDao()
    private val extensionDao = FakeSessionExtensionDao()
    private val prefs = mockk<PreferencesDataStore>()

    private fun repo(retention: RetentionLevel = RetentionLevel.INDEFINITE): SessionRepository {
        every { prefs.retentionLevel } returns flowOf(retention)
        return SessionRepository(sessionDao, extensionDao, prefs)
    }

    @Test
    fun `startSession is a no-op and returns sentinel when DONT_TRACK`() = runTest {
        val repository = repo(RetentionLevel.DONT_TRACK)

        val id = repository.startSession("com.example.app", "browsing")

        assertEquals(SessionRepository.NO_TRACK_SESSION_ID, id)
        assertTrue(sessionDao.rows.isEmpty())
    }

    @Test
    fun `startSession inserts and returns a real id otherwise`() = runTest {
        val repository = repo(RetentionLevel.INDEFINITE)

        val id = repository.startSession("com.example.app", "browsing")

        assertTrue(id != SessionRepository.NO_TRACK_SESSION_ID)
        assertEquals(1, sessionDao.rows.size)
        assertEquals("com.example.app", sessionDao.rows.single().packageName)
    }

    @Test
    fun `startExtension links the new session back to the extended one`() = runTest {
        val repository = repo(RetentionLevel.INDEFINITE)
        val extendedId = repository.startSession("com.example.app", null)

        val newId = repository.startExtension("com.example.app", extendedId, "just five more minutes")

        assertEquals(1, extensionDao.rows.size)
        val link = extensionDao.rows.single()
        assertEquals(extendedId, link.extendedSessionId)
        assertEquals(newId, link.newSessionId)
        assertEquals("just five more minutes", link.reason)
    }

    @Test
    fun `startExtension records nothing when DONT_TRACK`() = runTest {
        val repository = repo(RetentionLevel.DONT_TRACK)

        val newId = repository.startExtension("com.example.app", extendedSessionId = 42L, reason = "reason")

        assertEquals(SessionRepository.NO_TRACK_SESSION_ID, newId)
        assertTrue(extensionDao.rows.isEmpty())
    }

    @Test
    fun `getExtensionJustificationsSince merges modern and legacy rows, newest first`() = runTest {
        val repository = repo()
        sessionDao.rows += SessionEntity(
            id = 1, packageName = "com.legacy", startTime = 1_000L,
            intentDeclared = "Extension: old reason",
        )
        sessionDao.rows += SessionEntity(
            id = 2, packageName = "com.other", startTime = 500L,
            intentDeclared = "not an extension",
        )
        extensionDao.rows += SessionExtensionEntity(
            id = 1, packageName = "com.modern", extendedSessionId = 2, newSessionId = 3,
            reason = "new reason", timestamp = 2_000L,
        )

        val justifications = repository.getExtensionJustificationsSince(0L)

        assertEquals(2, justifications.size)
        // Newest first: modern (2000) before legacy (1000).
        assertEquals("com.modern", justifications[0].packageName)
        assertEquals("new reason", justifications[0].reason)
        assertEquals("com.legacy", justifications[1].packageName)
        assertEquals("old reason", justifications[1].reason)
    }

    @Test
    fun `getExtensionJustificationsSince respects the since cutoff`() = runTest {
        val repository = repo()
        sessionDao.rows += SessionEntity(
            id = 1, packageName = "com.legacy", startTime = 1_000L,
            intentDeclared = "Extension: too old",
        )
        extensionDao.rows += SessionExtensionEntity(
            id = 1, packageName = "com.modern", extendedSessionId = 1, newSessionId = 2,
            reason = "recent", timestamp = 5_000L,
        )

        val justifications = repository.getExtensionJustificationsSince(2_000L)

        assertEquals(1, justifications.size)
        assertEquals("recent", justifications.single().reason)
    }

    @Test
    fun `applyRetention is a no-op for INDEFINITE`() = runTest {
        val repository = repo()
        sessionDao.rows += SessionEntity(id = 1, packageName = "com.example.app", startTime = 0L)
        extensionDao.rows += SessionExtensionEntity(
            id = 1, packageName = "com.example.app", extendedSessionId = 1, newSessionId = 1,
            reason = "r", timestamp = 0L,
        )

        repository.applyRetention(RetentionLevel.INDEFINITE)

        assertEquals(1, sessionDao.rows.size)
        assertEquals(1, extensionDao.rows.size)
    }

    @Test
    fun `applyRetention prunes both tables past the cutoff`() = runTest {
        val repository = repo()
        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60_000L
        sessionDao.rows += SessionEntity(id = 1, packageName = "old", startTime = now - 10 * dayMs)
        sessionDao.rows += SessionEntity(id = 2, packageName = "recent", startTime = now - 1 * dayMs)
        extensionDao.rows += SessionExtensionEntity(
            id = 1, packageName = "old", extendedSessionId = 1, newSessionId = 1,
            reason = "r", timestamp = now - 10 * dayMs,
        )
        extensionDao.rows += SessionExtensionEntity(
            id = 2, packageName = "recent", extendedSessionId = 2, newSessionId = 2,
            reason = "r", timestamp = now - 1 * dayMs,
        )

        repository.applyRetention(RetentionLevel.DAYS_7)

        assertEquals(listOf("recent"), sessionDao.rows.map { it.packageName })
        assertEquals(listOf("recent"), extensionDao.rows.map { it.packageName })
    }

    @Test
    fun `exportCsv includes header and one row per session with joined reason`() = runTest {
        val repository = repo()
        sessionDao.rows += SessionEntity(
            id = 1, packageName = "com.example.app", startTime = 0L, endTime = 60_000L,
            intentDeclared = "check messages", extensionUsed = true,
        )
        extensionDao.rows += SessionExtensionEntity(
            id = 1, packageName = "com.example.app", extendedSessionId = 1, newSessionId = 1,
            reason = "one more minute", timestamp = 60_000L,
        )

        val csv = repository.exportCsv()
        val lines = csv.lines()

        assertEquals("startTime,endTime,packageName,durationMinutes,intentDeclared,extensionUsed,extensionReason", lines[0])
        assertEquals("0,60000,com.example.app,1,check messages,true,one more minute", lines[1])
    }

    @Test
    fun `exportCsv falls back to legacy Extension prefix when no session_extension row exists`() = runTest {
        val repository = repo()
        sessionDao.rows += SessionEntity(
            id = 1, packageName = "com.example.app", startTime = 0L, endTime = 0L,
            intentDeclared = "Extension: legacy reason",
        )

        val csv = repository.exportCsv()
        val row = csv.lines()[1]

        assertTrue(row.endsWith("legacy reason"))
    }

    @Test
    fun `exportCsv escapes commas, quotes and newlines`() = runTest {
        val repository = repo()
        sessionDao.rows += SessionEntity(
            id = 1, packageName = "com.example.app", startTime = 0L, endTime = 0L,
            intentDeclared = "reason, with \"quotes\"\nand a newline",
        )

        val csv = repository.exportCsv()

        // The comma/newline-containing field must be quoted, with embedded quotes doubled.
        // (csv.lines() is not used here since the field legitimately embeds a raw newline.)
        assertTrue(csv.contains("\"reason, with \"\"quotes\"\"\nand a newline\""))
    }

    @Test
    fun `exportCsv leaves plain fields unquoted`() = runTest {
        val repository = repo()
        sessionDao.rows += SessionEntity(
            id = 1, packageName = "com.example.app", startTime = 0L, endTime = 0L,
            intentDeclared = "plain text",
        )

        val csv = repository.exportCsv()

        assertTrue(csv.lines()[1].contains(",plain text,"))
    }
}
