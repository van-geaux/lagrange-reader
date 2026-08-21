package com.vangeaux.lagrange

import android.content.Context

class AppGraph private constructor(
    private val dependencies: Dependencies
) {
    private val repository = dependencies.repository
    private val sessionHistoryStore = dependencies.sessionHistoryStore
    val coordinator = dependencies.coordinator

    constructor(context: Context) : this(createDependencies(context.applicationContext))

    internal constructor(coordinator: AppCoordinator) : this(
        Dependencies(repository = null, sessionHistoryStore = null, coordinator = coordinator)
    )

    fun configureAudioPlayback(controller: ReadiumAudioPlaybackController) {
        val repository = repository ?: return
        val sessionHistoryStore = sessionHistoryStore ?: return
        controller.setStreamingAuthentication(
            headersProvider = { url ->
                repository.streamingRequestHeaders(url.toString())
            },
            recoverAuthentication = repository::recoverStreamingAuthentication
        )
        controller.setSessionHistoryStore(
            store = sessionHistoryStore,
            serverUrlProvider = repository::getServerUrl
        )
        coordinator.setAudioPlaybackOpener { state, playWhenReady ->
            controller.restorePersistedSession(state, playWhenReady)
        }
        coordinator.setAudioPlaybackCloser {
            controller.discardPendingOutboundSession()
            controller.close()
        }
        coordinator.setAudioSessionHistoryOpener(controller::openFromSessionHistory)
    }

    private data class Dependencies(
        val repository: BookOrbitRepository?,
        val sessionHistoryStore: AudiobookSessionHistoryStore?,
        val coordinator: AppCoordinator
    )

    companion object {
        private fun createDependencies(context: Context): Dependencies {
            val repository = BookOrbitRepository(context)
            val sessionHistoryStore = AudiobookSessionHistoryStore(context)
            val preferencesStore = AppPreferencesStore(context)
            val coordinator = AppCoordinator(
                repository,
                releaseChecker = GitHubReleaseChecker()::check,
                readIgnoredReleaseTag = preferencesStore::readIgnoredReleaseTag,
                saveIgnoredReleaseTag = preferencesStore::saveIgnoredReleaseTag,
                schedulerOverride = WorkManagerDownloadScheduler(context)
            ).also { it.setSessionHistoryStore(sessionHistoryStore) }
            return Dependencies(repository, sessionHistoryStore, coordinator)
        }
    }
}
