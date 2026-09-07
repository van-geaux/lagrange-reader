package com.vangeaux.lagrange

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
import java.util.UUID

internal fun browserStartIdentity(
    savedProcessIdentity: String?,
    savedStartIdentity: String?,
    currentProcessIdentity: String,
    newStartIdentity: String
): String = if (
    savedProcessIdentity == currentProcessIdentity && !savedStartIdentity.isNullOrBlank()
) {
    savedStartIdentity
} else {
    newStartIdentity
}

private const val BROWSER_START_PROCESS_ID_KEY = "browser_start_process_id"
private const val BROWSER_START_ID_KEY = "browser_start_id"
private val MAIN_PROCESS_IDENTITY = UUID.randomUUID().toString()

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
    private val browserStartIdentityState = mutableStateOf(UUID.randomUUID().toString())

    override fun onResume() {
        super.onResume()
        if (::preferencesStore.isInitialized) {
            appPreferencesState.value = preferencesStore.read()
        }
        if (::appCoordinator.isInitialized) {
            appCoordinator.checkForAppUpdate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        browserStartIdentityState.value = browserStartIdentity(
            savedProcessIdentity = savedInstanceState?.getString(BROWSER_START_PROCESS_ID_KEY),
            savedStartIdentity = savedInstanceState?.getString(BROWSER_START_ID_KEY),
            currentProcessIdentity = MAIN_PROCESS_IDENTITY,
            newStartIdentity = UUID.randomUUID().toString()
        )

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
            val browserStartIdentity by browserStartIdentityState
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
                        browserStartIdentity = browserStartIdentity,
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
                            preferencesStore.save(persisted)
                            appPreferences = persisted
                            if (refreshPolicyChanged) {
                                graph.coordinator.reconfigureBackgroundRefresh()
                            }
                        },
                        onDownloadReleaseUpdate = { update ->
                            startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(update.htmlUrl))
                            )
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    internal fun handleIncomingIntent(intent: Intent) {
        setIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            browserStartIdentityState.value = UUID.randomUUID().toString()
            if (::preferencesStore.isInitialized) {
                appPreferencesState.value = preferencesStore.read()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(BROWSER_START_PROCESS_ID_KEY, MAIN_PROCESS_IDENTITY)
        outState.putString(BROWSER_START_ID_KEY, browserStartIdentityState.value)
        super.onSaveInstanceState(outState)
    }
}

internal object MainActivityGraphProvider {
    @Volatile
    var testFactory: ((android.content.Context) -> AppGraph)? = null

    fun create(context: android.content.Context): AppGraph =
        testFactory?.invoke(context) ?: AppGraph(context)
}
