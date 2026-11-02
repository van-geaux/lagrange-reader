package com.vangeaux.lagrange

import java.nio.ByteBuffer
import java.io.ByteArrayInputStream
import java.util.zip.InflaterInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AudiobookCoverSelectionTest {
    @Test
    fun audiobookCoverIsPreferredBeforeGenericCover() {
        val book = BookSummary(
            libraryId = "library",
            id = "book",
            fileId = "audio-file",
            title = "Audio book",
            mediaKind = MediaKind.AUDIO,
            coverUrl = "https://server/book-cover",
            audiobookCoverUrl = "https://server/audio-cover"
        )

        assertEquals(
            listOf(
                "https://server/audio-cover",
                "https://server/api/v1/books/book/cover?medium=audio",
                "https://server/book-cover",
                "https://server/api/v1/books/book/thumbnail"
            ),
            bookCoverCandidateUrls(book, "https://server")
        )
    }

    @Test
    fun missingAudiobookCoverUrlFallsBackToAudioMediumAndBookCover() {
        val book = BookSummary(
            libraryId = "library",
            id = "book",
            fileId = "audio-file",
            title = "Audio book",
            mediaKind = MediaKind.AUDIO,
            coverUrl = "https://server/api/v1/books/book/cover",
            audiobookCoverUrl = null
        )

        assertEquals(
            listOf(
                "https://server/api/v1/books/book/cover?medium=audio",
                "https://server/api/v1/books/book/thumbnail"
            ),
            bookCoverCandidateUrls(book, "https://server")
        )
    }

    @Test
    fun nonAudioBooksDoNotUseAudiobookCoverCandidate() {
        val book = BookSummary(
            libraryId = "library",
            id = "book",
            fileId = "ebook-file",
            title = "Book",
            mediaKind = MediaKind.EPUB,
            coverUrl = "https://server/book-cover",
            audiobookCoverUrl = "https://server/audio-cover"
        )

        assertNull(bookCoverCandidateUrls(book, "https://server").firstOrNull { it.endsWith("audio-cover") })
    }

    @Test
    fun downloadedAudiobookIsNotCopiedToInternalStorageForCoverFallback() {
        assertFalse(shouldMaterializeDownloadedFileForCover(MediaKind.AUDIO))
        assertTrue(shouldMaterializeDownloadedFileForCover(MediaKind.EPUB))
        assertTrue(shouldMaterializeDownloadedFileForCover(MediaKind.PDF))
        assertTrue(shouldMaterializeDownloadedFileForCover(MediaKind.COMIC))
    }

    @Test
    fun padAndroidAutoCoverToSquareLeavesAlreadySquareArtworkUnchanged() {
        val originalBytes = encodeRgbaPng(48, 48, IntArray(48 * 48) { 0xFF2266AA.toInt() })

        val resultBytes = padAndroidAutoCoverToSquare(originalBytes)

        assertSame(originalBytes, resultBytes)
    }

    @Test
    fun padAndroidAutoCoverToSquarePadsPortraitArtworkWithoutCropping() {
        val srcWidth = 30
        val srcHeight = 60
        val solidRed = 0xFFCC3344.toInt()
        val portraitBytes = encodeRgbaPng(srcWidth, srcHeight, IntArray(srcWidth * srcHeight) { solidRed })

        val paddedBytes = padAndroidAutoCoverToSquare(portraitBytes)

        val width = ByteBuffer.wrap(paddedBytes, 16, 4).int
        val height = ByteBuffer.wrap(paddedBytes, 20, 4).int
        assertEquals(60, width)
        assertEquals(60, height)

        val pixels = decodeRgbaPngPixels(paddedBytes, width, height)
        // Left & right 15px padding columns are transparent (0), while full 30x60 artwork is centered at x=15..44
        assertEquals(0, (pixels[30 * width + 5] ushr 24) and 0xFF)
        assertEquals(0, (pixels[30 * width + 55] ushr 24) and 0xFF)
        assertEquals(solidRed, pixels[0 * width + 30])
        assertEquals(solidRed, pixels[59 * width + 30])
    }

    private fun decodeRgbaPngPixels(bytes: ByteArray, width: Int, height: Int): IntArray {
        var offset = 8
        val idat = java.io.ByteArrayOutputStream()
        while (offset + 8 <= bytes.size) {
            val len = ByteBuffer.wrap(bytes, offset, 4).int
            val type = String(bytes, offset + 4, 4, Charsets.US_ASCII)
            if (type == "IDAT") idat.write(bytes, offset + 8, len)
            if (type == "IEND") break
            offset += 12 + len
        }
        val raw = InflaterInputStream(ByteArrayInputStream(idat.toByteArray())).use { it.readBytes() }
        val stride = 1 + width * 4
        val out = IntArray(width * height)
        for (y in 0 until height) {
            val row = y * stride
            for (x in 0 until width) {
                val px = row + 1 + x * 4
                val r = raw[px].toInt() and 0xFF
                val g = raw[px + 1].toInt() and 0xFF
                val b = raw[px + 2].toInt() and 0xFF
                val a = raw[px + 3].toInt() and 0xFF
                out[y * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return out
    }
}
