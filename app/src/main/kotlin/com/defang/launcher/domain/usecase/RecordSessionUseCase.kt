package com.defang.launcher.domain.usecase

import com.defang.launcher.data.local.db.entity.SessionEntity
import com.defang.launcher.data.repository.SessionRepository
import javax.inject.Inject

class RecordSessionUseCase @Inject constructor(
    private val repo: SessionRepository,
) {
    /** Opens a new session record. Returns the new session ID. */
    suspend fun start(packageName: String, intentDeclared: String?, pattern: String? = null): Long =
        repo.startSession(packageName, intentDeclared, pattern)

    /** Opens the follow-on session for a gate extension, recording [reason]
     *  linked back to [extendedSessionId]. Returns the new session ID. */
    suspend fun startExtension(
        packageName: String,
        extendedSessionId: Long,
        reason: String,
        pattern: String? = null,
    ): Long = repo.startExtension(packageName, extendedSessionId, reason, pattern)

    /** Closes the session, optionally marking that the extension was used. */
    suspend fun end(sessionId: Long, extensionUsed: Boolean = false) =
        repo.endSession(sessionId, extensionUsed)

    /** The most recent session left open by an unexpected process death, if any. */
    suspend fun getOpenSession(): SessionEntity? = repo.getOpenSession()
}
