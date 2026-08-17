package com.defang.launcher.data.repository

import com.defang.launcher.data.local.datastore.PreferencesDataStore
import com.defang.launcher.data.local.db.dao.SessionDao
import com.defang.launcher.data.local.db.dao.SessionExtensionDao
import com.defang.launcher.data.local.db.entity.SessionEntity
import com.defang.launcher.data.local.db.entity.SessionExtensionEntity
import com.defang.launcher.domain.model.RetentionLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** A gate-extension justification, from either the current schema or legacy
 *  pre-v5 rows (see [SessionDao.getLegacyExtensionsSince]). */
data class ExtensionJustification(
    val packageName: String,
    val reason: String,
    val timestamp: Long,
)

@Singleton
class SessionRepository @Inject constructor(
    private val dao: SessionDao,
    private val extensionDao: SessionExtensionDao,
    private val prefs: PreferencesDataStore,
) {
    suspend fun startSession(packageName: String, intentDeclared: String?): Long {
        if (prefs.retentionLevel.first() == RetentionLevel.DONT_TRACK) return NO_TRACK_SESSION_ID
        val entity = SessionEntity(
            packageName = packageName,
            startTime = System.currentTimeMillis(),
            intentDeclared = intentDeclared,
        )
        return dao.insert(entity)
    }

    /** Starts the follow-on session for a gate extension and records [reason]
     *  linked back to [extendedSessionId], so it can be reviewed later. */
    suspend fun startExtension(packageName: String, extendedSessionId: Long, reason: String): Long {
        val newSessionId = startSession(packageName, intentDeclared = null)
        if (newSessionId != NO_TRACK_SESSION_ID) {
            extensionDao.insert(
                SessionExtensionEntity(
                    packageName = packageName,
                    extendedSessionId = extendedSessionId,
                    newSessionId = newSessionId,
                    reason = reason,
                    timestamp = System.currentTimeMillis(),
                )
            )
        }
        return newSessionId
    }

    suspend fun endSession(sessionId: Long, extensionUsed: Boolean = false) {
        val existing = dao.getById(sessionId) ?: return
        dao.update(
            existing.copy(
                endTime = System.currentTimeMillis(),
                extensionUsed = extensionUsed,
            )
        )
    }

    suspend fun markExtensionUsed(sessionId: Long) {
        val existing = dao.getById(sessionId) ?: return
        dao.update(existing.copy(extensionUsed = true))
    }

    fun observeSessionsForApp(packageName: String): Flow<List<SessionEntity>> =
        dao.observeForApp(packageName)

    suspend fun getSessionsSince(since: Long): List<SessionEntity> = dao.getSince(since)

    suspend fun countSince(since: Long): Int = dao.countSince(since)

    suspend fun getExtensionUsedToday(dayStartEpoch: Long): SessionEntity? =
        dao.getExtensionUsedToday(dayStartEpoch)

    suspend fun getExtensionJustificationsSince(since: Long): List<ExtensionJustification> {
        val modern = extensionDao.getSince(since).map {
            ExtensionJustification(it.packageName, it.reason, it.timestamp)
        }
        val legacy = dao.getLegacyExtensionsSince(since).map {
            ExtensionJustification(
                packageName = it.packageName,
                reason = it.intentDeclared.orEmpty().removePrefix("Extension: "),
                timestamp = it.startTime,
            )
        }
        return (modern + legacy).sortedByDescending { it.timestamp }
    }

    /** Prunes session data older than the configured [RetentionLevel]. No-op
     *  when the level is [RetentionLevel.INDEFINITE]. */
    suspend fun applyRetention(level: RetentionLevel) {
        val cutoff = level.cutoffMillis() ?: return
        dao.deleteOlderThan(cutoff)
        extensionDao.deleteOlderThan(cutoff)
    }

    suspend fun applyRetention() = applyRetention(prefs.retentionLevel.first())

    /** All session history as CSV, one row per session, with any extension
     *  reason (current or legacy) joined in. */
    suspend fun exportCsv(): String {
        val sessions = dao.getAll()
        val reasonsBySessionId = extensionDao.getAll().associateBy { it.newSessionId }
        val header = "startTime,endTime,packageName,durationMinutes,intentDeclared,extensionUsed,extensionReason"
        val rows = sessions.joinToString("\n") { s ->
            val reason = reasonsBySessionId[s.id]?.reason
                ?: s.intentDeclared?.takeIf { it.startsWith("Extension: ") }?.removePrefix("Extension: ")
            listOf(
                s.startTime.toString(),
                s.endTime.toString(),
                s.packageName,
                ((s.endTime - s.startTime).coerceAtLeast(0) / 60_000).toString(),
                s.intentDeclared.orEmpty(),
                s.extensionUsed.toString(),
                reason.orEmpty(),
            ).joinToString(",") { it.csvEscape() }
        }
        return "$header\n$rows"
    }

    private fun String.csvEscape(): String =
        if (any { it == ',' || it == '"' || it == '\n' }) {
            "\"${replace("\"", "\"\"")}\""
        } else this

    companion object {
        /** Sentinel returned instead of a real row id when retention is set to
         *  "don't track" — every id-based lookup already no-ops on a miss, so
         *  callers need no extra branching. */
        const val NO_TRACK_SESSION_ID = -1L
    }
}
