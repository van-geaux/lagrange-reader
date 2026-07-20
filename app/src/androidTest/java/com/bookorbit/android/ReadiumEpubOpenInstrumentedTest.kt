package com.bookorbit.android

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Size
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.publication.services.coverFitting

@RunWith(AndroidJUnit4::class)
class ReadiumEpubOpenInstrumentedTest {
    @Test
    fun opensEpubWithBitmapOnlySvgCover() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val epub = File(context.cacheDir, "readium-svg-cover.epub")
        writeSvgCoverEpub(epub)

        val result = openReadiumEpub(context, epub)
        assertTrue(result is ReadiumEpubOpenResult.Opened)
        val publication = (result as ReadiumEpubOpenResult.Opened).publication
        try {
            assertTrue(publication.conformsTo(Publication.Profile.EPUB))
            assertEquals("Readium SVG Cover", publication.metadata.title)
            assertEquals(1, publication.readingOrder.size)
            val cover = requireNotNull(publication.cover())
            assertTrue(cover.width > 0)
            assertTrue(cover.height > 0)
            val fittedCover = requireNotNull(publication.coverFitting(Size(60, 90)))
            assertTrue(fittedCover.width in 1..60)
            assertTrue(fittedCover.height in 1..90)
            cover.recycle()
            if (fittedCover !== cover) fittedCover.recycle()
        } finally {
            publication.close()
            epub.delete()
        }
    }

    private fun writeSvgCoverEpub(target: File) {
        val coverBytes = ByteArrayOutputStream().use { output ->
            Bitmap.createBitmap(120, 180, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.MAGENTA)
                compress(Bitmap.CompressFormat.JPEG, 92, output)
                recycle()
            }
            output.toByteArray()
        }
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            zip.writeStored("mimetype", "application/epub+zip".toByteArray())
            zip.writeDeflated(
                "META-INF/container.xml",
                """
                <?xml version="1.0"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """.trimIndent().toByteArray()
            )
            zip.writeDeflated(
                "OEBPS/content.opf",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:identifier id="book-id">readium-svg-cover</dc:identifier>
                    <dc:title>Readium SVG Cover</dc:title>
                    <dc:language>en</dc:language>
                    <meta property="dcterms:modified">2026-07-20T00:00:00Z</meta>
                  </metadata>
                  <manifest>
                    <item id="cover-page" href="Text/titlepage.xhtml" media-type="application/xhtml+xml"/>
                    <item id="cover" href="Images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>
                  </manifest>
                  <spine><itemref idref="cover-page"/></spine>
                </package>
                """.trimIndent().toByteArray()
            )
            zip.writeDeflated(
                "OEBPS/Text/titlepage.xhtml",
                """
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <head><title>Cover</title></head>
                  <body>
                    <svg xmlns="http://www.w3.org/2000/svg"
                         xmlns:xlink="http://www.w3.org/1999/xlink"
                         width="100%" height="100%" viewBox="0 0 120 180">
                      <image width="120" height="180" xlink:href="../Images/cover.jpg"/>
                    </svg>
                  </body>
                </html>
                """.trimIndent().toByteArray()
            )
            zip.writeDeflated("OEBPS/Images/cover.jpg", coverBytes)
        }
    }

    private fun ZipOutputStream.writeStored(name: String, bytes: ByteArray) {
        val crc = CRC32().apply { update(bytes) }
        putNextEntry(
            ZipEntry(name).apply {
                method = ZipEntry.STORED
                size = bytes.size.toLong()
                compressedSize = bytes.size.toLong()
                this.crc = crc.value
            }
        )
        write(bytes)
        closeEntry()
    }

    private fun ZipOutputStream.writeDeflated(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }
}
