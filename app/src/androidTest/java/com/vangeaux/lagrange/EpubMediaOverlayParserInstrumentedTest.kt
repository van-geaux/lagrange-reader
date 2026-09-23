package com.vangeaux.lagrange

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpubMediaOverlayParserInstrumentedTest {
    @Test
    fun parsesMediaOverlayUsingAndroidXmlProvider() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val epubFile = File(context.cacheDir, "epub-media-overlay-probe.epub")
        try {
            writeEpubFixture(epubFile)

            val playlist = EpubMediaOverlayParser.parse(epubFile)

            assertEquals(1, playlist.items.size)
            assertEquals("OEBPS/Text/chapter.xhtml", playlist.items.single().textHref)
            assertEquals("sentence-one", playlist.items.single().textFragment)
            assertEquals("OEBPS/Audio/clip.mp3", playlist.items.single().audioHref)
        } finally {
            epubFile.delete()
        }
    }

    private fun writeEpubFixture(file: File) {
        val entries = linkedMapOf(
            "META-INF/container.xml" to """
                <?xml version="1.0" encoding="UTF-8"?>
                <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                    <rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles>
                </container>
            """.trimIndent().toByteArray(),
            "OEBPS/content.opf" to """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                    <manifest>
                        <item id="chapter" href="Text/chapter.xhtml" media-type="application/xhtml+xml" media-overlay="chapter-overlay"/>
                        <item id="chapter-overlay" href="Overlays/chapter.smil" media-type="application/smil+xml"/>
                        <item id="chapter-audio" href="Audio/clip.mp3" media-type="audio/mpeg"/>
                    </manifest>
                    <spine><itemref idref="chapter"/></spine>
                </package>
            """.trimIndent().toByteArray(),
            "OEBPS/Overlays/chapter.smil" to """
                <?xml version="1.0" encoding="UTF-8"?>
                <smil xmlns="http://www.w3.org/ns/SMIL">
                    <body><seq><par>
                        <text src="../Text/chapter.xhtml#sentence-one"/>
                        <audio src="../Audio/clip.mp3" clipBegin="0.0s" clipEnd="1.0s"/>
                    </par></seq></body>
                </smil>
            """.trimIndent().toByteArray(),
            "OEBPS/Text/chapter.xhtml" to "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body/></html>".toByteArray(),
            "OEBPS/Audio/clip.mp3" to byteArrayOf(1, 2, 3)
        )
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            entries.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }
}
