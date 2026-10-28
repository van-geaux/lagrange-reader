package com.vangeaux.lagrange.provider

import com.vangeaux.lagrange.*

import android.content.Context
import com.vangeaux.lagrange.provider.bookorbit.BookOrbitModuleSet
import com.vangeaux.lagrange.provider.bookorbit.BookOrbitOidcModule
import com.vangeaux.lagrange.provider.bookorbit.BookOrbitOidcModuleImpl

import com.vangeaux.lagrange.core.AchievementModule
import com.vangeaux.lagrange.core.AnnotationModule
import com.vangeaux.lagrange.core.DownloadModule
import com.vangeaux.lagrange.core.LocalBooksModule
import com.vangeaux.lagrange.core.ReadingProgressModule
import com.vangeaux.lagrange.core.ReadingSessionModule
import com.vangeaux.lagrange.core.ReadingStatusModule
import com.vangeaux.lagrange.core.StatisticsModule
import com.vangeaux.lagrange.core.SmartScopeModule
import com.vangeaux.lagrange.core.AuthorModule
import com.vangeaux.lagrange.core.BookDetailModule
import com.vangeaux.lagrange.core.BookCoverModule
import com.vangeaux.lagrange.core.BookCatalogModule
import com.vangeaux.lagrange.core.ReaderModule
import com.vangeaux.lagrange.core.SeriesCatalogModule
import com.vangeaux.lagrange.core.ActiveReaderSession
import com.vangeaux.lagrange.core.HomeShelfModule
import com.vangeaux.lagrange.core.LibraryModule
import com.vangeaux.lagrange.core.LoginModule
import com.vangeaux.lagrange.core.ServerDetectionModule
import com.vangeaux.lagrange.core.ServerSelectionModule
import com.vangeaux.lagrange.core.CacheModule
import com.vangeaux.lagrange.core.ProviderFeatureAvailability
import com.vangeaux.lagrange.core.AuthenticatedMediaModule
import com.vangeaux.lagrange.core.BackgroundCacheModule
import com.vangeaux.lagrange.core.ProviderSessionModule
import com.vangeaux.lagrange.provider.komga.KomgaRepository
import com.vangeaux.lagrange.provider.komga.KomgaModuleSet
import com.vangeaux.lagrange.provider.kavita.KavitaRepository
import com.vangeaux.lagrange.provider.kavita.KavitaModuleSet

internal fun resolveProviderDownloadModule(repository: BookOrbitDataSource): DownloadModule =
    when (repository) {
        is KomgaRepository -> KomgaModuleSet(repository).downloads
        is KavitaRepository -> KavitaModuleSet(repository).modules.downloads
        else -> BookOrbitModuleSet(repository).modules.downloads
    }

internal fun resolveProviderRepository(context: Context, serverUrl: String): BookOrbitDataSource {
    val profiles = ServerProfileStore(context)
    val profile = profiles.active()?.takeIf { serverUrlsMatch(it.serverUrl, serverUrl) }
        ?: profiles.readAll().firstOrNull { serverUrlsMatch(it.serverUrl, serverUrl) }
    return when (profile?.providerId) {
        PROVIDER_KOMGA -> KomgaRepository(context)
        PROVIDER_KAVITA -> KavitaRepository(context)
        else -> BookOrbitRepository(context)
    }
}

internal fun resolveProviderLocalBooksModule(repository: BookOrbitDataSource): LocalBooksModule =
    when (repository) {
        is KomgaRepository -> KomgaModuleSet(repository).localBooks
        is KavitaRepository -> KavitaModuleSet(repository).modules.localBooks
        else -> BookOrbitModuleSet(repository).modules.localBooks
    }

internal fun resolveProviderReadingStatusModule(repository: BookOrbitDataSource): ReadingStatusModule =
    when (repository) {
        is KomgaRepository -> KomgaModuleSet(repository).readingStatus
        is KavitaRepository -> KavitaModuleSet(repository).modules.readingStatus
        else -> BookOrbitModuleSet(repository).modules.readingStatus
    }

internal fun resolveProviderReadingProgressModule(repository: BookOrbitDataSource): ReadingProgressModule =
    when (repository) {
        is KomgaRepository -> KomgaModuleSet(repository).readingProgress
        is KavitaRepository -> KavitaModuleSet(repository).modules.readingProgress
        else -> BookOrbitModuleSet(repository).modules.readingProgress
    }

internal fun resolveProviderReadingSessionModule(repository: BookOrbitDataSource): ReadingSessionModule =
    when (repository) {
        is KomgaRepository -> KomgaModuleSet(repository).readingSessions
        is KavitaRepository -> KavitaModuleSet(repository).modules.readingSessions
        else -> BookOrbitModuleSet(repository).modules.readingSessions
    }

internal fun resolveProviderAnnotationModule(repository: BookOrbitDataSource): AnnotationModule =
    when (repository) {
        is KomgaRepository -> KomgaModuleSet(repository).annotations
        is KavitaRepository -> KavitaModuleSet(repository).modules.annotations
        else -> BookOrbitModuleSet(repository).modules.annotations
    }

internal fun resolveProviderAchievementModule(repository: BookOrbitDataSource): AchievementModule =
    when (repository) {
        is KomgaRepository -> KomgaModuleSet(repository).achievements
        is KavitaRepository -> KavitaModuleSet(repository).modules.achievements
        else -> BookOrbitModuleSet(repository).modules.achievements
    }

internal fun resolveProviderStatisticsModule(repository: BookOrbitDataSource): StatisticsModule =
    when (repository) {
        is KomgaRepository -> KomgaModuleSet(repository).statistics
        is KavitaRepository -> KavitaModuleSet(repository).modules.statistics
        else -> BookOrbitModuleSet(repository).modules.statistics
    }

internal fun resolveProviderSmartScopeModule(repository: BookOrbitDataSource): SmartScopeModule =
    when (repository) {
        is KomgaRepository -> KomgaModuleSet(repository).smartScopes
        is KavitaRepository -> KavitaModuleSet(repository).modules.smartScopes
        else -> BookOrbitModuleSet(repository).modules.smartScopes
    }

internal fun resolveProviderAuthorModule(repository: BookOrbitDataSource): AuthorModule =
    when (repository) {
        is KomgaRepository -> KomgaModuleSet(repository).authors
        is KavitaRepository -> KavitaModuleSet(repository).modules.authors
        else -> BookOrbitModuleSet(repository).modules.authors
    }

internal fun resolveProviderBookDetailModule(repository: BookOrbitDataSource): BookDetailModule =
    when (repository) {
        is KomgaRepository -> KomgaModuleSet(repository).bookDetail
        is KavitaRepository -> KavitaModuleSet(repository).modules.bookDetail
        else -> BookOrbitModuleSet(repository).modules.bookDetail
    }

internal fun resolveProviderBookCoverModule(repository: BookOrbitDataSource): BookCoverModule =
    when (repository) {
        is KomgaRepository -> KomgaModuleSet(repository).covers
        is KavitaRepository -> KavitaModuleSet(repository).modules.covers
        else -> BookOrbitModuleSet(repository).modules.covers
    }

internal fun resolveProviderBookCatalogModule(repository: BookOrbitDataSource): BookCatalogModule =
    when (repository) {
        is KomgaRepository -> KomgaModuleSet(repository).bookCatalog
        is KavitaRepository -> KavitaModuleSet(repository).modules.bookCatalog
        else -> BookOrbitModuleSet(repository).modules.bookCatalog
    }

internal fun resolveProviderSeriesCatalogModule(repository: BookOrbitDataSource): SeriesCatalogModule =
    when (repository) {
        is KomgaRepository -> KomgaModuleSet(repository).seriesCatalog
        is KavitaRepository -> KavitaModuleSet(repository).modules.seriesCatalog
        else -> BookOrbitModuleSet(repository).modules.seriesCatalog
    }

internal fun resolveProviderReaderModule(repository: BookOrbitDataSource): ReaderModule =
    when (repository) {
        is KomgaRepository -> KomgaModuleSet(repository).reader
        is KavitaRepository -> KavitaModuleSet(repository).modules.reader
        else -> BookOrbitModuleSet(repository).modules.reader
    }

internal fun resolveProviderHomeShelfModule(repository: BookOrbitDataSource): HomeShelfModule =
    when (repository) {
        is KomgaRepository -> KomgaModuleSet(repository).home
        is KavitaRepository -> KavitaModuleSet(repository).modules.home
        else -> BookOrbitModuleSet(repository).modules.home
    }

internal fun resolveProviderLibraryModule(repository: BookOrbitDataSource): LibraryModule =
    when (repository) {
        is KomgaRepository -> KomgaModuleSet(repository).libraries
        is KavitaRepository -> KavitaModuleSet(repository).modules.libraries
        else -> BookOrbitModuleSet(repository).modules.libraries
    }

internal fun resolveProviderLoginModule(repository: BookOrbitDataSource): LoginModule =
    when (repository) {
        is KomgaRepository -> KomgaModuleSet(repository).login
        is KavitaRepository -> KavitaModuleSet(repository).modules.login
        else -> object : LoginModule {
        override suspend fun getSessionState() = repository.getSessionState()
        override suspend fun login(username: String, password: String) = repository.login(username, password)
        override suspend fun clearSession() = repository.clearSession()
        }
    }

internal fun resolveProviderServerDetectionModule(repository: BookOrbitDataSource): ServerDetectionModule =
    when (repository) {
        is KomgaRepository -> KomgaModuleSet(repository).serverDetection
        is KavitaRepository -> KavitaModuleSet(repository).modules.serverDetection
        else -> object : ServerDetectionModule {
        override suspend fun canReachServer(serverUrl: String) = repository.canReachServer(serverUrl)
        override suspend fun checkServer(serverUrl: String) = repository.checkServer(serverUrl)
        }
    }

internal fun resolveProviderServerSelectionModule(repository: BookOrbitDataSource): ServerSelectionModule =
    when (repository) {
        is KomgaRepository -> KomgaModuleSet(repository).serverSelection
        is KavitaRepository -> KavitaModuleSet(repository).modules.serverSelection
        else -> object : ServerSelectionModule {
        override suspend fun getServerUrl() = repository.getServerUrl()
        override suspend fun setServerUrl(serverUrl: String) = repository.setServerUrl(serverUrl)
        override suspend fun clearServer() = repository.clearServer()
        override suspend fun getSelectedLibraryId() = repository.getSelectedLibraryId()
        override suspend fun setSelectedLibraryId(libraryId: String) = repository.setSelectedLibraryId(libraryId)
        }
    }

internal fun resolveProviderCacheModule(repository: BookOrbitDataSource): CacheModule =
    when (repository) {
        is KomgaRepository -> KomgaModuleSet(repository).cache
        is KavitaRepository -> KavitaModuleSet(repository).modules.cache
        else -> object : CacheModule {
        override suspend fun loadCachedBrowserState() = repository.loadCachedBrowserState()
        override suspend fun loadStorageUsage() = repository.loadStorageUsage()
        override suspend fun clearAppCache() = repository.clearAppCache()
        override suspend fun loadOfflineCacheStatus() = repository.loadOfflineCacheStatus()
        override suspend fun startOfflineCacheUpdate() = repository.startOfflineCacheUpdate()
        override suspend fun cancelOfflineCacheUpdate() = repository.cancelOfflineCacheUpdate()
        override suspend fun clearOfflineCache() = repository.clearOfflineCache()
        override suspend fun reconfigureBackgroundRefresh() = repository.reconfigureBackgroundRefresh()
        }
    }

internal fun resolveProviderFeatureAvailability(repository: BookOrbitDataSource): ProviderFeatureAvailability =
    when (repository) {
        is KomgaRepository -> KomgaModuleSet(repository).featureAvailability
        is KavitaRepository -> KavitaModuleSet(repository).featureAvailability
        else -> ProviderFeatureAvailability()
    }

internal fun resolveProviderSessionModule(repository: BookOrbitDataSource): ProviderSessionModule =
    when (repository) {
        is KomgaRepository -> KomgaModuleSet(repository).session
        is KavitaRepository -> KavitaModuleSet(repository).session
        else -> object : ProviderSessionModule {
        override suspend fun saveCurrentProfileSession(): Boolean =
            (repository as? ProfileSessionAware)?.let { it.saveCurrentProfileSession(); true } ?: false

        override suspend fun restoreCurrentProfileSession(): Boolean =
            (repository as? ProfileSessionAware)?.restoreCurrentProfileSession() == true
        }
    }

internal fun resolveProviderBookOrbitOidcModule(repository: BookOrbitDataSource): BookOrbitOidcModule? =
    if (repository is BookOrbitRepository) BookOrbitOidcModuleImpl(repository) else null

internal fun resolveProviderAuthenticatedMediaModule(repository: BookOrbitDataSource): AuthenticatedMediaModule =
    when (repository) {
        is KomgaRepository -> KomgaModuleSet(repository).authenticatedMedia
        is KavitaRepository -> KavitaModuleSet(repository).authenticatedMedia
        else -> object : AuthenticatedMediaModule {
        override suspend fun requestHeaders(url: String): Map<String, String> = when (repository) {
            is BookOrbitRepository -> repository.streamingRequestHeaders(url)
            is KomgaRepository -> repository.streamingRequestHeaders(url)
            else -> emptyMap()
        }

        override suspend fun recoverAuthentication(): Boolean = when (repository) {
            is BookOrbitRepository -> repository.recoverStreamingAuthentication()
            is KomgaRepository -> repository.recoverStreamingAuthentication()
            else -> false
        }
        }
    }

internal fun resolveProviderBackgroundCacheModule(repository: BookOrbitDataSource): BackgroundCacheModule =
    when (repository) {
        is KomgaRepository -> KomgaModuleSet(repository).backgroundCache
        is KavitaRepository -> KavitaModuleSet(repository).backgroundCache
        else -> object : BackgroundCacheModule {
        override val isAvailable: Boolean = repository is BookOrbitRepository

        override suspend fun warmCoverCacheBatch(
            expectedServerUrl: String,
            libraryId: String,
            startIndex: Int,
            maxDownloads: Int
        ): Int? = (repository as? BookOrbitRepository)?.warmCoverCacheBatch(
            expectedServerUrl, libraryId, startIndex, maxDownloads
        )

        override suspend fun warmOfflineCacheBatch(
            expectedServerUrl: String,
            libraryId: String,
            startIndex: Int,
            maxItems: Int,
            includeDetails: Boolean,
            includeCovers: Boolean
        ): OfflineCacheBatchResult? = (repository as? BookOrbitRepository)?.warmOfflineCacheBatch(
            expectedServerUrl, libraryId, startIndex, maxItems, includeDetails, includeCovers
        )
        }
    }

private fun activeProviderRepository(context: Context): BookOrbitDataSource {
    val profile = ServerProfileStore(context).active()
    return profile?.serverUrl?.let { resolveProviderRepository(context, it) } ?: BookOrbitRepository(context)
}

internal fun resolveActiveProviderReadingProgressModule(context: Context): ReadingProgressModule = resolveProviderReadingProgressModule(activeProviderRepository(context))
internal fun resolveActiveProviderReadingSessionModule(context: Context): ReadingSessionModule = resolveProviderReadingSessionModule(activeProviderRepository(context))
internal fun resolveActiveProviderAnnotationModule(context: Context): AnnotationModule = resolveProviderAnnotationModule(activeProviderRepository(context))
internal suspend fun resolveActiveProviderAuthenticatedMediaModule(context: Context): AuthenticatedMediaModule =
    resolveProviderAuthenticatedMediaModule(activeProviderRepository(context))
