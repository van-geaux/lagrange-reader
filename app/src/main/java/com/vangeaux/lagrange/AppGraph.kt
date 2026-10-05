package com.vangeaux.lagrange

import android.content.Context
import com.vangeaux.lagrange.provider.komga.KomgaRepository
import com.vangeaux.lagrange.core.DownloadModule
import com.vangeaux.lagrange.provider.bookorbit.BookOrbitModuleSet

import com.vangeaux.lagrange.provider.*
import com.vangeaux.lagrange.epubtts.EpubTtsModuleImpl

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
        val sessionHistoryStore = sessionHistoryStore ?: return
        val reconfigure: (BookOrbitDataSource) -> Unit = { source ->
            val bookOrbitRepository = source as? BookOrbitRepository
            if (bookOrbitRepository == null) {
                coordinator.setAudioPlaybackOpener(null)
                coordinator.setAudioSessionHistoryOpener(null)
            } else {
                controller.setStreamingAuthentication(
                    headersProvider = { url ->
                        bookOrbitRepository.streamingRequestHeaders(url.toString())
                    },
                    recoverAuthentication = bookOrbitRepository::recoverStreamingAuthentication
                )
                controller.setSessionHistoryStore(
                    store = sessionHistoryStore,
                    serverUrlProvider = bookOrbitRepository::getServerUrl
                )
                coordinator.setAudioPlaybackOpener { state, playWhenReady ->
                    controller.restorePersistedSession(state, playWhenReady)
                }
                coordinator.setAudioSessionHistoryOpener(controller::openFromSessionHistory)
            }
        }
        coordinator.setAudioPlaybackReconfigurer(reconfigure)
        repository?.let(reconfigure)
        coordinator.setAudioPlaybackCloser {
            controller.discardPendingOutboundSession()
            controller.close()
        }
    }

    private data class Dependencies(
        val repository: BookOrbitDataSource?,
        val sessionHistoryStore: AudiobookSessionHistoryStore?,
        val coordinator: AppCoordinator
    )

    companion object {
        private fun createDependencies(context: Context): Dependencies {
            val serverProfileStore = ServerProfileStore(context)
            val activeProfile = serverProfileStore.active()
            val repository: BookOrbitDataSource = if (activeProfile?.providerId == "komga") {
                KomgaRepository(context)
            } else {
                BookOrbitRepository(context)
            }
            val sessionHistoryStore = AudiobookSessionHistoryStore(context)
            val preferencesStore = AppPreferencesStore(context)
            val repositoryResolver = ProviderRepositoryResolver(context)
            val coordinator = AppCoordinator(
                repository,
                releaseChecker = GitHubReleaseChecker()::check,
                readIgnoredReleaseTag = preferencesStore::readIgnoredReleaseTag,
                saveIgnoredReleaseTag = preferencesStore::saveIgnoredReleaseTag,
                schedulerOverride = WorkManagerDownloadScheduler(context),
                serverProfileStore = serverProfileStore,
                repositoryResolver = repositoryResolver::resolve,
                readerLifecycleModule = ReaderLifecycleModuleImpl(context),
                downloadModuleResolver = ::resolveProviderDownloadModule,
                localBooksModuleResolver = ::resolveProviderLocalBooksModule,
                readingStatusModuleResolver = ::resolveProviderReadingStatusModule,
                readingProgressModuleResolver = ::resolveProviderReadingProgressModule,
                readingSessionModuleResolver = ::resolveProviderReadingSessionModule,
                annotationModuleResolver = ::resolveProviderAnnotationModule,
                achievementModuleResolver = ::resolveProviderAchievementModule,
                statisticsModuleResolver = ::resolveProviderStatisticsModule
                , smartScopeModuleResolver = ::resolveProviderSmartScopeModule
                , authorModuleResolver = ::resolveProviderAuthorModule
                , bookDetailModuleResolver = ::resolveProviderBookDetailModule
                , bookCoverModuleResolver = ::resolveProviderBookCoverModule
                , bookCatalogModuleResolver = ::resolveProviderBookCatalogModule
                , homeShelfModuleResolver = ::resolveProviderHomeShelfModule
                , libraryModuleResolver = ::resolveProviderLibraryModule
                , loginModuleResolver = ::resolveProviderLoginModule
                , serverDetectionModuleResolver = ::resolveProviderServerDetectionModule
                , readerModuleResolver = ::resolveProviderReaderModule
                , seriesCatalogModuleResolver = ::resolveProviderSeriesCatalogModule
                , serverSelectionModuleResolver = ::resolveProviderServerSelectionModule
                , cacheModuleResolver = ::resolveProviderCacheModule
                , featureAvailabilityResolver = ::resolveProviderFeatureAvailability
                , providerSessionModuleResolver = ::resolveProviderSessionModule
                , bookOrbitOidcModuleResolver = ::resolveProviderBookOrbitOidcModule
            ).also {
                it.setSessionHistoryStore(sessionHistoryStore)
                it.setEpubTtsPlaybackCloser {
                    EpubTtsModuleImpl.stopAndAwait(context)
                }
            }
            return Dependencies(repository, sessionHistoryStore, coordinator)
        }
    }
}
