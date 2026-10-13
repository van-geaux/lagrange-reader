package com.vangeaux.lagrange

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutomaticDownloadPlannerWorkerInstrumentedTest {
    @Test
    fun plannerQueuesOneScopedAutomaticAttemptForAnEligibleBook() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workConfiguration = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, workConfiguration)
        val workManager = WorkManager.getInstance(context)
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = when {
                        request.path == "/api/v1/auth/me" -> """{"id":"planner-user"}"""
                        request.path == "/api/v1/libraries" ->
                            """[{"id":"lib-planner","name":"Planner Library"}]"""
                        request.path == "/api/v1/libraries/lib-planner/books" -> """
                            {
                              "items": [{
                                "id":"book-planner",
                                "title":"Planner Book",
                                "files":[{
                                  "id":"file-planner",
                                  "format":"epub",
                                  "role":"primary",
                                  "filename":"planner.epub",
                                  "sizeBytes":100
                                }]
                              }],
                              "total":1,
                              "page":0,
                              "size":50
                            }
                        """.trimIndent()
                        request.path == "/api/v1/books/book-planner" -> """
                            {
                              "id":"book-planner",
                              "libraryId":"lib-planner",
                              "title":"Planner Book",
                              "files":[{
                                "id":"file-planner",
                                "format":"epub",
                                "role":"primary",
                                "filename":"planner.epub",
                                "sizeBytes":100
                              }]
                            }
                        """.trimIndent()
                        else -> return MockResponse().setResponseCode(404)
                    }
                    return MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(body)
                }
            }
            start()
        }
        val serverUrl = server.url("/").toString().trimEnd('/')
        val profileId = "planner-worker-test-${System.nanoTime()}"
        val profileStore = ServerProfileStore(context)
        val repository = BookOrbitRepository(context)
        val originalServerUrl = repository.getServerUrl()
        val profilePreferences = context.getSharedPreferences("server_profiles", Context.MODE_PRIVATE)
        val originalProfilesJson = profilePreferences.getString("profiles", null)
        val originalActiveProfileId = profilePreferences.getString("active_profile_id", null)
        val browserSnapshotFile = File(context.filesDir, "browser_snapshot.json")
        val originalBrowserSnapshot = browserSnapshotFile.takeIf(File::isFile)?.readBytes()
        val downloadStore = DownloadStore(context)
        val storagePolicyPreferences = context.getSharedPreferences(
            "local_book_storage_policy",
            Context.MODE_PRIVATE
        )
        val originalStoragePolicy = storagePolicyPreferences.getString("policy", null)
        var testStorageScopeId: String? = null

        try {
            if (!originalServerUrl.isNullOrBlank()) repository.saveCurrentProfileSession()
            profileStore.upsert(
                ServerProfile(
                    id = profileId,
                    serverUrl = serverUrl,
                    providerId = PROVIDER_BOOKORBIT,
                    displayName = "Planner worker test"
                )
            )
            repository.setServerUrl(serverUrl)
            ProfileSessionStore(context).write(profileId, "planner-test-token")
            assertTrue(repository.restoreCurrentProfileSession())
            AuthenticatedAccountScopeStore(context).writeAuthoritative(
                profileId = profileId,
                providerId = PROVIDER_BOOKORBIT,
                principal = "id:planner-user"
            )
            val storageScopeId = requireNotNull(
                downloadStorageScopeId(context, serverUrl, profileId)
            )
            testStorageScopeId = storageScopeId
            val policy = AutomaticDownloadPolicyStore(context).save(
                AutomaticDownloadPolicy(
                    profileId = profileId,
                    serverUrl = serverUrl,
                    storageScopeId = storageScopeId,
                    enabled = true,
                    selectedLibraryIds = setOf("lib-planner"),
                    unmeteredOnly = true,
                    chargingRequired = true,
                    reserveBytes = 0L,
                    maximumBytes = 0L
                )
            )
            val worker = TestListenableWorkerBuilder<AutomaticDownloadPlannerWorker>(
                context = context,
                inputData = AutomaticDownloadPlannerWorker.Requests.input(
                    profileId,
                    serverUrl,
                    storageScopeId,
                    policy.generation
                )
            ).build()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success()::class.java, result::class.java)
            val queued = downloadStore.readDownloadQueue(serverUrl, storageScopeId).single()
            assertEquals(DownloadOrigin.AUTOMATIC, queued.origin)
            assertEquals(storageScopeId, queued.storageScopeId)
            assertEquals(policy.generation, queued.policyGeneration)
            val attempt = downloadStore.readAttempts(serverUrl, storageScopeId).single()
            assertEquals(queued.requestId, attempt.requestId)
            assertEquals(DownloadOrigin.AUTOMATIC, attempt.origin)
            assertEquals(storageScopeId, attempt.storageScopeId)
            assertEquals(policy.generation, attempt.policyGeneration)
            assertTrue(server.requestCount >= 4)

            AutomaticDownloadScheduler.pauseForInactiveProfile(context, policy)

            val preservedPolicy = AutomaticDownloadPolicyStore(context)
                .read(profileId, serverUrl, storageScopeId)
            assertTrue(preservedPolicy.enabled)
            assertEquals(policy.generation, preservedPolicy.generation)
            assertEquals(
                AutomaticDownloadRunState.PAUSED,
                AutomaticDownloadStatusStore(context)
                    .read(profileId, storageScopeId)
                    .state
            )
        } finally {
            val cleanupFailures = mutableListOf<Throwable>()
            suspend fun cleanUp(block: suspend () -> Unit) {
                runCatching { block() }.exceptionOrNull()?.let(cleanupFailures::add)
            }
            cleanUp {
                workManager.cancelAllWorkByTag(downloadServerTag(serverUrl)).result
                    .get(5L, TimeUnit.SECONDS)
            }
            cleanUp { downloadStore.clearDownloadQueue(serverUrl) }
            cleanUp {
                downloadStore.readAttempts(serverUrl).forEach { attempt ->
                    downloadStore.removeAttempt(serverUrl, attempt.fileId)
                }
            }
            if (originalServerUrl.isNullOrBlank()) {
                cleanUp { repository.clearSession() }
                cleanUp { repository.clearServer() }
            }
            cleanUp {
                BookDetailCacheStore(context).remove(
                    serverUrl,
                    "book-planner",
                    "file-planner"
                )
            }
            testStorageScopeId?.let { storageScopeId ->
                cleanUp { AutomaticDownloadStateStore(context).clearScans(storageScopeId) }
                cleanUp {
                    check(context.getSharedPreferences(
                        "automatic_download_policies",
                        Context.MODE_PRIVATE
                    ).edit().remove(
                        scopedPreferenceKey("policy", profileId, storageScopeId)
                    ).commit())
                }
                cleanUp {
                    check(context.getSharedPreferences(
                        "automatic_download_status",
                        Context.MODE_PRIVATE
                    ).edit().remove(
                        scopedPreferenceKey("status", profileId, storageScopeId)
                    ).commit())
                }
            }
            cleanUp {
                check(storagePolicyPreferences.edit().apply {
                    if (originalStoragePolicy == null) remove("policy")
                    else putString("policy", originalStoragePolicy)
                }.commit())
            }
            cleanUp { ProfileSessionStore(context).clear(profileId) }
            cleanUp { AuthenticatedAccountScopeStore(context).clear(profileId) }
            cleanUp {
                check(profilePreferences.edit().apply {
                    if (originalProfilesJson == null) remove("profiles")
                    else putString("profiles", originalProfilesJson)
                    if (originalActiveProfileId == null) remove("active_profile_id")
                    else putString("active_profile_id", originalActiveProfileId)
                }.commit())
            }
            cleanUp {
                if (originalBrowserSnapshot == null) {
                    if (browserSnapshotFile.exists()) check(browserSnapshotFile.delete())
                } else {
                    browserSnapshotFile.parentFile?.mkdirs()
                    browserSnapshotFile.writeBytes(originalBrowserSnapshot)
                }
            }
            if (!originalServerUrl.isNullOrBlank()) {
                cleanUp { repository.setServerUrl(originalServerUrl) }
                cleanUp { repository.restoreCurrentProfileSession() }
            }
            cleanUp { server.shutdown() }
            cleanupFailures.firstOrNull()?.let { first ->
                cleanupFailures.drop(1).forEach(first::addSuppressed)
                throw AssertionError("Automatic-download worker test cleanup failed.", first)
            }
        }
    }

    private fun scopedPreferenceKey(
        prefix: String,
        profileId: String,
        storageScopeId: String
    ): String = prefix + "_" + Base64.encodeToString(
        "$profileId\u0000$storageScopeId".toByteArray(Charsets.UTF_8),
        Base64.NO_WRAP or Base64.URL_SAFE
    )
}
