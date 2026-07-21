package com.bookorbit.android

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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
        delay(2_000L)
        application.audioPlaybackController.close()
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
