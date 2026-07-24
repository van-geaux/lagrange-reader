package com.bookorbit.android

import android.content.Context

class AppGraph(context: Context) {
    private val repository = BookOrbitRepository(context.applicationContext)
    private val sessionHistoryStore = AudiobookSessionHistoryStore(context.applicationContext)
    private val preferencesStore = AppPreferencesStore(context.applicationContext)
    val coordinator = AppCoordinator(
        repository,
        releaseChecker = GitHubReleaseChecker()::check,
        readIgnoredReleaseTag = preferencesStore::readIgnoredReleaseTag,
        saveIgnoredReleaseTag = preferencesStore::saveIgnoredReleaseTag
    ).also { it.setSessionHistoryStore(sessionHistoryStore) }

    fun configureAudioPlayback(controller: ReadiumAudioPlaybackController) {
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
        coordinator.setAudioSessionHistoryOpener(controller::openFromSessionHistory)
    }
}
