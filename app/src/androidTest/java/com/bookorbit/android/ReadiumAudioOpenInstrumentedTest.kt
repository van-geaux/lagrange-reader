package com.bookorbit.android

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadiumAudioOpenInstrumentedTest {
    @Test
    fun opensExternalM4bFixtureThroughService() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<BookOrbitApplication>()
        val fixture = File(
            requireNotNull(application.getExternalFilesDir(null)),
            FIXTURE_NAME
        )
        assumeTrue("Push $FIXTURE_NAME into the app external-files directory first.", fixture.isFile)
        val book = BookSummary(
            libraryId = "fixture-library",
            id = "fixture-audiobook",
            fileId = "fixture-m4b",
            title = "Eighty-Six, Vol. 01",
            author = "Asato Asato",
            format = "m4b",
            mediaKind = MediaKind.AUDIO,
            localPath = fixture.absolutePath
        )

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

    private companion object {
        const val FIXTURE_NAME = "readium-audio-sample.m4b"
    }
}
