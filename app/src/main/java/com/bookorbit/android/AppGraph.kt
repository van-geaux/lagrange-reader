package com.bookorbit.android

import android.content.Context

class AppGraph(context: Context) {
    private val repository = BookOrbitRepository(context.applicationContext)
    val coordinator = AppCoordinator(repository)

    fun configureAudioPlayback(controller: ReadiumAudioPlaybackController) {
        controller.setStreamingAuthentication(
            headersProvider = { url ->
                repository.streamingRequestHeaders(url.toString())
            },
            recoverAuthentication = repository::recoverStreamingAuthentication
        )
        coordinator.setAudioPlaybackOpener { state, playWhenReady ->
            controller.restorePersistedSession(state, playWhenReady)
        }
    }
}
