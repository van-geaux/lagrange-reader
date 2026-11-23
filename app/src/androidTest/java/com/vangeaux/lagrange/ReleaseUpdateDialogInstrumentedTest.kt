package com.vangeaux.lagrange

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReleaseUpdateDialogInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun signerMismatchShowsDataLossWarningAndOpensReleasePageWithoutDownloading() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val downloadCount = AtomicInteger()
        val releasePageCount = AtomicInteger()
        composeRule.setContent {
            BookOrbitTheme {
                ReleaseUpdateDialog(
                    update = releaseUpdate(),
                    downloadState = ReleaseDownloadState(
                        signerStatus = ReleaseSignerStatus.MISMATCH
                    ),
                    onDownload = { downloadCount.incrementAndGet() },
                    onOpenReleasePage = { releasePageCount.incrementAndGet() },
                    onIgnore = {}
                )
            }
        }

        composeRule.onNodeWithText(
            context.getString(R.string.release_download_signer_mismatch)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.release_download_open_release)
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("release-update-download").performClick()

        assertEquals(0, downloadCount.get())
        assertEquals(1, releasePageCount.get())
    }

    @Test
    fun unknownSignerShowsWarningAndOpensReleasePageWithoutDownloading() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val downloadCount = AtomicInteger()
        val releasePageCount = AtomicInteger()
        composeRule.setContent {
            BookOrbitTheme {
                ReleaseUpdateDialog(
                    update = releaseUpdate(),
                    downloadState = ReleaseDownloadState(
                        signerStatus = ReleaseSignerStatus.UNKNOWN
                    ),
                    onDownload = { downloadCount.incrementAndGet() },
                    onOpenReleasePage = { releasePageCount.incrementAndGet() },
                    onIgnore = {}
                )
            }
        }

        composeRule.onNodeWithText(
            context.getString(R.string.release_download_signer_unknown)
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("release-update-download").performClick()

        assertEquals(0, downloadCount.get())
        assertEquals(1, releasePageCount.get())
    }

    @Test
    fun failedDownloadedApkShowsWarningBeforeNotesAndOpensReleasePage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val downloadCount = AtomicInteger()
        val releasePageCount = AtomicInteger()
        composeRule.setContent {
            BookOrbitTheme {
                ReleaseUpdateDialog(
                    update = releaseUpdate(),
                    downloadState = ReleaseDownloadState(apkVerificationFailed = true),
                    onDownload = { downloadCount.incrementAndGet() },
                    onOpenReleasePage = { releasePageCount.incrementAndGet() },
                    onIgnore = {}
                )
            }
        }

        val warningNode = composeRule.onNodeWithText(
            context.getString(R.string.release_download_apk_verification_failed)
        )
        warningNode.assertIsDisplayed()
        val warningTop = warningNode.fetchSemanticsNode().boundsInRoot.top
        val firstReleaseNoteTop = composeRule.onNodeWithText(
            "Release note section 1:",
            substring = true
        ).fetchSemanticsNode().boundsInRoot.top
        assertTrue("APK warning must be above release notes.", warningTop < firstReleaseNoteTop)
        composeRule.onNodeWithText(
            context.getString(R.string.release_download_open_release)
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("release-update-download").performClick()

        assertEquals(0, downloadCount.get())
        assertEquals(1, releasePageCount.get())
    }

    private fun releaseUpdate() = ReleaseUpdate(
        versionName = "2.1.4",
        tagName = "v2.1.4",
        title = "Lagrange 2.1.4",
        notes = (1..18).joinToString("\n\n") { section ->
            "Release note section $section: " +
                "This update improves library discovery, reader controls, provider integrations, and background download reliability. ".repeat(3)
        },
        htmlUrl = "https://github.com/van-geaux/lagrange-reader/releases/tag/v2.1.4",
        apkDownloadUrl = "https://github.com/van-geaux/lagrange-reader/releases/download/v2.1.4/Lagrange-2.1.4.apk",
        apkFileName = "Lagrange-2.1.4.apk"
    )
}
