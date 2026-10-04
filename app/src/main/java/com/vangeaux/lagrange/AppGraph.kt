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
    private val bookOrbitRepository = dependencies.bookOrbitRepository
    private val sessionHistoryStore = dependencies.sessionHistoryStore
    val coordinator = dependencies.coordinator

    constructor(context: Context) : this(createDependencies(context.applicationContext))

    internal constructor(coordinator: AppCoordinator) : this(
        Dependencies(repository = null, sessionHistoryStore = null, coordinator = coordinator, bookOrbitRepository = null)
    )

    fun configureAudioPlayback(controller: ReadiumAudioPlaybackController) {
        val repository = bookOrbitRepository ?: return
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
        val repository: BookOrbitDataSource?,
        val sessionHistoryStore: AudiobookSessionHistoryStore?,
        val coordinator: AppCoordinator,
        val bookOrbitRepository: BookOrbitRepository?
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
            val bookOrbitRepository = repository as? BookOrbitRepository
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
            return Dependencies(repository, sessionHistoryStore, coordinator, bookOrbitRepository)
        }
    }
}
