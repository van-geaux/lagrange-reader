package com.vangeaux.lagrange

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubMediaOverlayPlaybackTest {
    @Test
    fun `selection resolves to the media overlay sentence containing its fragment`() {
        val clips = listOf(
            playableClip(0, "OPS/chapter.xhtml", "sentence-a").clip.copy(sectionIndex = 0),
            playableClip(1, "OPS/chapter.xhtml", "sentence-b").clip.copy(sectionIndex = 0),
            playableClip(2, "OPS/next.xhtml", "sentence-c").clip.copy(sectionIndex = 1)
        )

        assertEquals(1, epubMediaOverlayClipIndexForSelection(clips, 0, "sentence-b"))
        assertEquals(null, epubMediaOverlayClipIndexForSelection(clips, 1, "sentence-b"))
        assertEquals(null, epubMediaOverlayClipIndexForSelection(clips, 0, null))
    }

    @Test
    fun `player starts only after navigation reaches the active narration clip`() = runBlocking {
        val navigation = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val start = async {
            prepareEpubMediaOverlayThenNavigateAndPlay(
                prepare = { events += "prepare" },
                navigate = {
                    events += "navigate-start"
                    navigation.await()
                    events += "navigate-complete"
                },
                play = { events += "play" }
            )
        }

        yield()
        assertEquals(listOf("prepare", "navigate-start"), events)
        navigation.complete(Unit)
        start.await()
        assertEquals(listOf("prepare", "navigate-start", "navigate-complete", "play"), events)
    }

    @Test
    fun `resume chooses exact text href and fragment`() {
        val clips = listOf(
            playableClip(0, "OPS/chapter.xhtml", "sentence-a"),
            playableClip(1, "OPS/chapter.xhtml", "sentence-b")
        )

        assertEquals(1, epubMediaOverlayStartIndex(clips, "OPS/chapter.xhtml#sentence-b"))
        assertEquals(0, epubMediaOverlayStartIndex(clips, "OPS/chapter.xhtml#missing"))
        assertEquals(0, epubMediaOverlayStartIndex(clips, null))
    }

    @Test
    fun `clipping configuration matches the SMIL audio interval`() {
        val clip = playableClip(0, "OPS/chapter.xhtml", "sentence-a")
        val configuration = epubMediaOverlayClippingConfiguration(
            clip.clip.copy(clipBeginSeconds = 0.25, clipEndSeconds = 1.5)
        )

        assertEquals(250L, configuration.startPositionMs)
        assertEquals(1_500L, configuration.endPositionMs)
    }

    @Test
    fun `extracts only referenced audio to a path-safe cache file`() = runBlocking {
        val epub = Files.createTempFile("overlay-resource", ".epub").toFile()
        val cache = Files.createTempDirectory("overlay-cache").toFile()
        ZipOutputStream(epub.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("OPS/audio/voice.mp3"))
            zip.write(byteArrayOf(1, 2, 3, 4))
            zip.closeEntry()
        }
        val clip = EpubMediaOverlayClip(
            index = 0,
            sectionIndex = 0,
            textHref = "OPS/chapter.xhtml",
            textFragment = "sentence-a",
            audioHref = "OPS/audio/voice.mp3",
            clipBeginSeconds = 0.25,
            clipEndSeconds = 1.5,
            durationSeconds = 1.25
        )

        val extracted = EpubMediaOverlayResources.extract(
            epubFile = epub,
            playlist = EpubMediaOverlayPlaylist(listOf(clip)),
            cacheDir = cache,
            readerKey = "book|file"
        )

        assertEquals(1, extracted.size)
        assertTrue(extracted.single().audioFile.isFile)
        assertEquals("1, 2, 3, 4", extracted.single().audioFile.readBytes().joinToString(", "))
        assertTrue(extracted.single().audioFile.canonicalPath.startsWith(cache.canonicalPath))
    }

    private fun playableClip(index: Int, href: String, fragment: String) = EpubMediaOverlayPlayableClip(
        clip = EpubMediaOverlayClip(
            index = index,
            sectionIndex = 0,
            textHref = href,
            textFragment = fragment,
            audioHref = "OPS/audio/voice.mp3",
            clipBeginSeconds = 0.0,
            clipEndSeconds = 1.0,
            durationSeconds = 1.0
        ),
        audioFile = File("audio-$index.mp3")
    )
}
