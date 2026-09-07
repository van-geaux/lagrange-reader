package com.vangeaux.lagrange

import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class EpubImageLibraryTest {
    @Test
    fun `source preparation requires consent before remote caching`() {
        assertEquals(
            EpubImageLibrarySourceResult.RemoteConsentRequired,
            epubImageLibrarySourceResult(
                localFile = null,
                localFileError = null,
                fileId = "file-1",
                allowRemoteCache = false
            )
        )
        assertEquals(
            EpubImageLibrarySourceResult.Unavailable("The selected EPUB is not available locally."),
            epubImageLibrarySourceResult(
                localFile = null,
                localFileError = null,
                fileId = "file-1",
                allowRemoteCache = true
            )
        )
    }

    @Test
    fun `scan orders referenced images by spine appearance and appends unreferenced raster entries`() {
        val epub = zipFile(
            "mimetype" to "application/epub+zip",
            "META-INF/container.xml" to containerXml("OPS/package.opf"),
            "OPS/package.opf" to opfXml(
                manifest = listOf(
                    "chapter-one" to "text/chapter-one.xhtml",
                    "chapter-two" to "text/chapter-two.xhtml",
                    "first" to "../Images/first.jpg",
                    "second" to "../Images/second.png",
                    "third" to "../Images/third.webp",
                    "unreferenced" to "../Images/unreferenced.gif"
                ),
                spine = listOf("chapter-one", "chapter-two")
            ),
            "OPS/text/chapter-one.xhtml" to xhtml(
                "../Images/second.png",
                "../Images/first.jpg",
                "../Images/second.png"
            ),
            "OPS/text/chapter-two.xhtml" to xhtml("../Images/third.webp"),
            "OPS/Images/first.jpg" to "image" ,
            "OPS/Images/second.png" to "image",
            "OPS/Images/third.webp" to "image",
            "OPS/Images/unreferenced.gif" to "image"
        )

        val dimensions = mapOf(
            "OPS/Images/first.jpg" to EpubImageDimensions(800, 600),
            "OPS/Images/second.png" to EpubImageDimensions(900, 900),
            "OPS/Images/third.webp" to EpubImageDimensions(1000, 700),
            "OPS/Images/unreferenced.gif" to EpubImageDimensions(700, 700)
        )

        val result = EpubImageLibraryScanner.scan(
            epub,
            minimumDimensionPx = 250,
            probe = { path, _ -> dimensions[path] }
        )

        assertEquals(
            listOf(
                "OPS/Images/second.png",
                "OPS/Images/first.jpg",
                "OPS/Images/third.webp",
                "OPS/Images/unreferenced.gif"
            ),
            result.entries.map(EpubImageEntry::archivePath)
        )
    }

    @Test
    fun `scan filters images below either dimension and excludes SVG`() {
        val epub = zipFile(
            "META-INF/container.xml" to containerXml("package.opf"),
            "package.opf" to opfXml(
                manifest = listOf(
                    "content" to "content.xhtml",
                    "short-width" to "short-width.png",
                    "short-height" to "short-height.jpg",
                    "exact" to "exact.webp",
                    "vector" to "vector.svg"
                ),
                spine = listOf("content")
            ),
            "content.xhtml" to xhtml(
                "short-width.png",
                "short-height.jpg",
                "exact.webp",
                "vector.svg"
            ),
            "short-width.png" to "image",
            "short-height.jpg" to "image",
            "exact.webp" to "image",
            "vector.svg" to "image"
        )

        val dimensions = mapOf(
            "short-width.png" to EpubImageDimensions(249, 1000),
            "short-height.jpg" to EpubImageDimensions(1000, 249),
            "exact.webp" to EpubImageDimensions(250, 250)
        )

        val result = EpubImageLibraryScanner.scan(
            epub,
            minimumDimensionPx = 250,
            probe = { path, _ -> dimensions[path] }
        )

        assertEquals(listOf("exact.webp"), result.entries.map(EpubImageEntry::archivePath))
        assertEquals(3, result.skippedCount)
    }

    private fun zipFile(vararg entries: Pair<String, String>): java.io.File {
        val file = Files.createTempFile("epub-image-library", ".epub").toFile()
        ZipOutputStream(file.outputStream()).use { output ->
            entries.forEach { (path, content) ->
                output.putNextEntry(ZipEntry(path))
                output.write(content.toByteArray())
                output.closeEntry()
            }
        }
        return file.apply { deleteOnExit() }
    }

    private fun containerXml(rootfile: String): String =
        """<?xml version="1.0"?><container><rootfiles><rootfile full-path="$rootfile"/></rootfiles></container>"""

    private fun opfXml(manifest: List<Pair<String, String>>, spine: List<String>): String =
        buildString {
            append("<package><manifest>")
            manifest.forEach { (id, href) ->
                append("<item id=\"$id\" href=\"$href\" media-type=\"image/unknown\"/>")
            }
            append("</manifest><spine>")
            spine.forEach { id -> append("<itemref idref=\"$id\"/>") }
            append("</spine></package>")
        }

    private fun xhtml(vararg imagePaths: String): String = buildString {
        append("<html><body>")
        imagePaths.forEach { path -> append("<img src=\"$path\"/>") }
        append("</body></html>")
    }
}
