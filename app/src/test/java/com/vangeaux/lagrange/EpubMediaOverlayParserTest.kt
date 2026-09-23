package com.vangeaux.lagrange

import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubMediaOverlayParserTest {
    @Test
    fun `parses spine ordered SMIL sentence audio clips`() {
        val epub = epubFile(
            "META-INF/container.xml" to """<container><rootfiles><rootfile full-path="OPS/package.opf"/></rootfiles></container>""",
            "OPS/package.opf" to """
                <package><manifest>
                  <item id="chapter" href="text/chapter.xhtml" media-type="application/xhtml+xml" media-overlay="overlay"/>
                  <item id="overlay" href="overlays/chapter.smil" media-type="application/smil+xml"/>
                  <item id="audio" href="audio/chapter.mp3" media-type="audio/mpeg"/>
                </manifest><spine><itemref idref="chapter"/></spine></package>
            """,
            "OPS/overlays/chapter.smil" to """
                <smil xmlns="http://www.w3.org/ns/SMIL"><body><seq>
                  <par><text src="../text/chapter.xhtml#sentence-1"/><audio src="../audio/chapter.mp3" clipBegin="npt=0.5s" clipEnd="npt=2.25s"/></par>
                  <par><text src="../text/chapter.xhtml#sentence-2"/><audio src="../audio/chapter.mp3" clipBegin="2.25s" clipEnd="4s"/></par>
                  <par><text src="../text/chapter.xhtml#invalid"/><audio src="../audio/chapter.mp3" clipBegin="5s" clipEnd="4s"/></par>
                </seq></body></smil>
            """,
            "OPS/text/chapter.xhtml" to "<html><body>Read-along text</body></html>",
            "OPS/audio/chapter.mp3" to "audio"
        )

        val playlist = EpubMediaOverlayParser.parse(epub)

        assertEquals(2, playlist.items.size)
        assertEquals("OPS/text/chapter.xhtml", playlist.items[0].textHref)
        assertEquals("sentence-1", playlist.items[0].textFragment)
        assertEquals("OPS/audio/chapter.mp3", playlist.items[0].audioHref)
        assertEquals(0.5, playlist.items[0].clipBeginSeconds, 0.0001)
        assertEquals(2.25, playlist.items[0].clipEndSeconds!!, 0.0001)
        assertEquals(1.75, playlist.items[0].durationSeconds!!, 0.0001)
        assertEquals("sentence-2", playlist.items[1].textFragment)
        assertEquals(4.0, playlist.items[1].clipEndSeconds!!, 0.0001)
    }

    @Test
    fun `EPUB without media overlays returns an empty playlist`() {
        val epub = epubFile(
            "META-INF/container.xml" to """<container><rootfiles><rootfile full-path="package.opf"/></rootfiles></container>""",
            "package.opf" to """<package><manifest><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="chapter"/></spine></package>""",
            "chapter.xhtml" to "<html><body>Text only</body></html>"
        )

        val playlist = EpubMediaOverlayParser.parse(epub)

        assertTrue(playlist.items.isEmpty())
    }

    @Test
    fun `parses clock time values and rejects invalid duration ranges`() {
        assertEquals(62.5, parseEpubMediaOverlayClock("npt=1:02.5" )!!, 0.0001)
        assertEquals(1.5, parseEpubMediaOverlayClock("1500ms")!!, 0.0001)
        assertNull(parseEpubMediaOverlayClock("-1s"))
        assertNull(parseEpubMediaOverlayClock("1:60"))
        assertNull(parseEpubMediaOverlayClock("1:02:60"))
        assertNull(parseEpubMediaOverlayClock("not-a-clock"))
        assertNull(epubMediaOverlayDuration(4.0, 2.0))
    }

    private fun epubFile(vararg entries: Pair<String, String>): java.io.File {
        val file = Files.createTempFile("epub-media-overlay", ".epub").toFile()
        ZipOutputStream(file.outputStream()).use { output ->
            entries.forEach { (path, content) ->
                output.putNextEntry(ZipEntry(path))
                output.write(content.toByteArray())
                output.closeEntry()
            }
        }
        return file.apply { deleteOnExit() }
    }
}
