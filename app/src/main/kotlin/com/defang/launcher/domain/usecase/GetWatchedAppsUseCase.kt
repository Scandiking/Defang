package com.defang.launcher.domain.usecase

import com.defang.launcher.data.repository.AppConfigRepository
import com.defang.launcher.domain.model.AppConfig
import com.defang.launcher.domain.model.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetWatchedAppsUseCase @Inject constructor(
    private val repo: AppConfigRepository,
) {
    operator fun invoke(): Flow<List<AppConfig>> =
        repo.observeWatched().map { list -> list.map { it.toDomain() } }
}
