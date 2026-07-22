package com.bookorbit.android

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadiumAudioOpenInstrumentedTest {
    @Test
    fun opensWhenCompactPlayerObservesSessionBeforePlaybackStarts() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<BookOrbitApplication>()
        val fixture = File(
            requireNotNull(application.getExternalFilesDir(null)),
            FIXTURE_NAME
        )
        assumeTrue("Push $FIXTURE_NAME into the app external-files directory first.", fixture.isFile)
        val book = fixtureBook(fixture)
        val controller = application.audioPlaybackController

        val observedSession = async {
            controller.session().first { it != null }
        }
        delay(250L)

        val result = withTimeoutOrNull(30_000L) {
            controller.open(
                book = book,
                file = fixture,
                initialPositionMs = 0L,
                launchMode = ReaderLaunchMode.NORMAL,
                playWhenReady = true
            )
        } ?: fail("Opening the real M4B timed out.")

        assertTrue(result is ReadiumAudioOpenResult.Opened)
        assertEquals(AudioPlaybackPreparationState.IDLE, controller.preparationState.value)
        assertNull(controller.preparingSession.value)
        withTimeoutOrNull(5_000L) { observedSession.await() }
            ?: fail("The compact-player session observer did not receive the opened session.")
        controller.close()
    }

    @Test
    fun opensExternalM4bFixtureThroughService() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<BookOrbitApplication>()
        val fixture = File(
            requireNotNull(application.getExternalFilesDir(null)),
            FIXTURE_NAME
        )
        assumeTrue("Push $FIXTURE_NAME into the app external-files directory first.", fixture.isFile)
        val book = fixtureBook(fixture)

        val result = application.audioPlaybackController.open(
            book = book,
            file = fixture,
            initialPositionMs = 0L,
            launchMode = ReaderLaunchMode.NORMAL,
            playWhenReady = true
        )
        assertTrue(result is ReadiumAudioOpenResult.Opened)
        assertEquals(
            AudioPlaybackPreparationState.IDLE,
            application.audioPlaybackController.preparationState.value
        )
        assertNull(application.audioPlaybackController.preparingSession.value)
        delay(2_000L)
        application.audioPlaybackController.close()
    }

    @Test
    fun unsupportedStreamFormatResetsPreparationState() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<BookOrbitApplication>()
        val controller = application.audioPlaybackController
        val book = BookSummary(
            libraryId = "fixture-library",
            id = "unsupported-stream",
            fileId = "unsupported-file",
            title = "Unsupported audiobook",
            format = "epub",
            mediaKind = MediaKind.AUDIO
        )

        try {
            val result = withTimeoutOrNull(15_000L) {
                controller.open(
                    book = book,
                    streamUrl = "https://example.invalid/audiobook.epub",
                    initialPositionMs = 0L,
                    launchMode = ReaderLaunchMode.NORMAL,
                    playWhenReady = false
                )
            } ?: fail("Opening an unsupported stream did not return within the test timeout.")

            assertTrue(result is ReadiumAudioOpenResult.Error)
            assertEquals(AudioPlaybackPreparationState.IDLE, controller.preparationState.value)
            assertNull(controller.preparingSession.value)
        } finally {
            withTimeoutOrNull(5_000L) { controller.close() }
                ?: fail("Audiobook cleanup did not complete within the test timeout.")
        }
    }

    private fun fixtureBook(fixture: File): BookSummary = BookSummary(
        libraryId = "fixture-library",
        id = "fixture-audiobook",
        fileId = "fixture-m4b",
        title = "Eighty-Six, Vol. 01",
        author = "Asato Asato",
        format = "m4b",
        mediaKind = MediaKind.AUDIO,
        localPath = fixture.absolutePath
    )

    private companion object {
        const val FIXTURE_NAME = "readium-audio-sample.m4b"
    }
}
