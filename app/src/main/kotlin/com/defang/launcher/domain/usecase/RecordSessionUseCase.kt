package com.defang.launcher.domain.usecase

import com.defang.launcher.data.repository.SessionRepository
import javax.inject.Inject

class RecordSessionUseCase @Inject constructor(
    private val repo: SessionRepository,
) {
    /** Opens a new session record. Returns the new session ID. */
    suspend fun start(packageName: String, intentDeclared: String?): Long =
        repo.startSession(packageName, intentDeclared)

    /** Opens the follow-on session for a gate extension, recording [reason]
     *  linked back to [extendedSessionId]. Returns the new session ID. */
    suspend fun startExtension(packageName: String, extendedSessionId: Long, reason: String): Long =
        repo.startExtension(packageName, extendedSessionId, reason)

    /** Closes the session, optionally marking that the extension was used. */
    suspend fun end(sessionId: Long, extensionUsed: Boolean = false) =
        repo.endSession(sessionId, extensionUsed)
}
