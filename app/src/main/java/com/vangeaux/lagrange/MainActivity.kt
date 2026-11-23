package com.vangeaux.lagrange

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal fun requestedOrientationForLock(
    enabled: Boolean,
    lockedOrientation: LockedOrientation = LockedOrientation.PORTRAIT
): Int = when {
    !enabled -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    lockedOrientation == LockedOrientation.LANDSCAPE ->
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
}

internal fun preferencesForOrientationLockChange(
    previous: AppPreferences,
    updated: AppPreferences,
    currentConfigurationOrientation: Int
): AppPreferences {
    if (!updated.lockOrientation || previous.lockOrientation) return updated
    val lockedOrientation = if (
        currentConfigurationOrientation == Configuration.ORIENTATION_LANDSCAPE
    ) {
        LockedOrientation.LANDSCAPE
    } else {
        LockedOrientation.PORTRAIT
    }
    return updated.copy(lockedOrientation = lockedOrientation)
}

class MainActivity : ComponentActivity() {
    private lateinit var preferencesStore: AppPreferencesStore
    private lateinit var appCoordinator: AppCoordinator
    private val appPreferencesState = mutableStateOf(AppPreferences())
    private val releaseDownloadState = mutableStateOf(ReleaseDownloadState())
    private var releaseDownloadJob: Job? = null
    private var releaseDownloadGeneration = 0L

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onResume() {
        super.onResume()
        if (::preferencesStore.isInitialized) {
            appPreferencesState.value = preferencesStore.read()
        }
        if (::appCoordinator.isInitialized) {
            appCoordinator.checkForAppUpdate()
        }
    }

    private fun downloadReleaseApk(update: ReleaseUpdate) {
        if (releaseDownloadState.value.downloading) return
        val signerStatus = ReleaseSignerCompatibility.currentInstalledStatus(this)
        ReleaseSignerCompatibility.routeUpdateAction(
            status = signerStatus,
            onDownload = { downloadReleaseApkAfterSignerCheck(update) },
            onManualSwitch = {
                releaseDownloadState.value = ReleaseDownloadState(signerStatus = signerStatus)
            }
        )
    }

    private fun downloadReleaseApkAfterSignerCheck(update: ReleaseUpdate) {
        if (releaseDownloadState.value.downloading) return
        val generation = ++releaseDownloadGeneration
        releaseDownloadState.value = ReleaseDownloadState(downloading = true)
        releaseDownloadJob = lifecycleScope.launch {
            try {
                ReleaseApkInstaller(this@MainActivity).downloadAndInstall(
                    update = update,
                    onProgress = { progress ->
                        runOnUiThread {
                            if (generation == releaseDownloadGeneration &&
                                releaseDownloadState.value.downloading
                            ) {
                                releaseDownloadState.value = releaseDownloadState.value.copy(
                                    progressPercent = progress
                                )
                            }
                        }
                    },
                    onInstallPermissionRequired = {
                        if (generation == releaseDownloadGeneration) {
                            releaseDownloadState.value = ReleaseDownloadState(
                                errorMessage = getString(R.string.release_download_permission_required)
                            )
                        }
                    }
                )
                if (generation == releaseDownloadGeneration &&
                    releaseDownloadState.value.errorMessage == null
                ) {
                    releaseDownloadState.value = ReleaseDownloadState()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: ReleaseApkVerificationException) {
                if (generation == releaseDownloadGeneration) {
                    releaseDownloadState.value = ReleaseDownloadState(apkVerificationFailed = true)
                }
            } catch (error: Exception) {
                if (generation == releaseDownloadGeneration) {
                    releaseDownloadState.value = ReleaseDownloadState(
                        errorMessage = error.localizedMessage ?: getString(R.string.release_download_failed)
                    )
                }
            } finally {
                if (generation == releaseDownloadGeneration) {
                    releaseDownloadJob = null
                }
            }
        }
    }

    private fun cancelReleaseDownload() {
        releaseDownloadGeneration += 1
        releaseDownloadJob?.cancel()
        releaseDownloadJob = null
        releaseDownloadState.value = ReleaseDownloadState()
    }

    private fun openGitHubReleasePage(update: ReleaseUpdate) {
        val releaseUri = Uri.Builder()
            .scheme("https")
            .authority("github.com")
            .appendPath("van-geaux")
            .appendPath("lagrange-reader")
            .appendPath("releases")
            .appendPath("tag")
            .appendPath(update.tagName)
            .build()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, releaseUri))
        } catch (_: ActivityNotFoundException) {
            releaseDownloadState.value = releaseDownloadState.value.copy(
                errorMessage = getString(R.string.release_download_browser_unavailable)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        ReleaseApkCache.cleanupInstalledOrOlder(cacheDir, BuildConfig.VERSION_NAME)

        val graph = MainActivityGraphProvider.create(this)
        appCoordinator = graph.coordinator
        graph.coordinator.reconfigureBackgroundRefresh()
        preferencesStore = AppPreferencesStore(this)
        val initialPreferences = preferencesStore.read()
        appPreferencesState.value = initialPreferences
        requestedOrientation = requestedOrientationForLock(
            enabled = initialPreferences.lockOrientation,
            lockedOrientation = initialPreferences.lockedOrientation
        )
        val audioPlaybackController =
            (application as BookOrbitApplication).audioPlaybackController
        graph.configureAudioPlayback(audioPlaybackController)
        audioPlaybackController.setProgressListener(graph.coordinator::onAudioPlaybackProgress)
        audioPlaybackController.setCoverLoader(graph.coordinator::loadBookCover)
        splashScreen.setKeepOnScreenCondition {
            graph.coordinator.screen.value is AppScreen.Loading
        }

        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            show(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            val screen by graph.coordinator.screen.collectAsState()
            var appPreferences by appPreferencesState
            LaunchedEffect(Unit) {
                graph.coordinator.bootstrap()
            }
            LaunchedEffect(
                appPreferences.lockOrientation,
                appPreferences.lockedOrientation
            ) {
                requestedOrientation = requestedOrientationForLock(
                    enabled = appPreferences.lockOrientation,
                    lockedOrientation = appPreferences.lockedOrientation
                )
            }
            CompositionLocalProvider(
                LocalReduceMotion provides appPreferences.reduceMotion
            ) {
                BookOrbitTheme(themeMode = appPreferences.themeMode) {
                    BookOrbitApp(
                        screen = screen,
                        coordinator = graph.coordinator,
                        audioPlaybackController = audioPlaybackController,
                        appPreferences = appPreferences,
                        onAppPreferencesChange = { updated ->
                            val persisted = preferencesForOrientationLockChange(
                                previous = appPreferences,
                                updated = updated,
                                currentConfigurationOrientation =
                                    resources.configuration.orientation
                            )
                            val refreshPolicyChanged =
                                persisted.backgroundRefreshNetworkPolicy !=
                                    appPreferences.backgroundRefreshNetworkPolicy ||
                                    persisted.offlineCacheLibraryIds != appPreferences.offlineCacheLibraryIds ||
                                    persisted.offlineCacheDetailsEnabled != appPreferences.offlineCacheDetailsEnabled ||
                                    persisted.offlineCacheCoversEnabled != appPreferences.offlineCacheCoversEnabled ||
                                    persisted.offlineCacheAutoRefreshEnabled !=
                                    appPreferences.offlineCacheAutoRefreshEnabled
                            val audioInterruptionPolicyChanged =
                                persisted.pauseAudiobookForAudioInterruptions !=
                                    appPreferences.pauseAudiobookForAudioInterruptions
                            preferencesStore.save(persisted)
                            appPreferences = persisted
                            if (audioInterruptionPolicyChanged) {
                                audioPlaybackController.updateAudioInterruptionPolicy(
                                    persisted.pauseAudiobookForAudioInterruptions
                                )
                            }
                            if (refreshPolicyChanged) {
                                graph.coordinator.reconfigureBackgroundRefresh()
                            }
                        },
                        releaseDownloadState = releaseDownloadState.value,
                        onDownloadReleaseUpdate = ::downloadReleaseApk,
                        onOpenReleasePage = ::openGitHubReleasePage,
                        onClearReleaseDownloadState = ::cancelReleaseDownload
                    )
                }
            }
        }
    }
}

internal object MainActivityGraphProvider {
    @Volatile
    var testFactory: ((android.content.Context) -> AppGraph)? = null

    fun create(context: android.content.Context): AppGraph =
        testFactory?.invoke(context) ?: AppGraph(context)
}
