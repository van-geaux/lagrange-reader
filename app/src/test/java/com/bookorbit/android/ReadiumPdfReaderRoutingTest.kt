package com.bookorbit.android

import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadiumPdfReaderRoutingTest {
    @Test
    fun `readable pdf routes to Readium`() {
        val file = Files.createTempFile("readium-route", ".pdf").toFile().apply {
            writeBytes("%PDF-1.4\n".toByteArray())
        }

        assertTrue(shouldUseReadiumPdfReader(MediaKind.PDF, file))
        assertFalse(shouldUseReadiumPdfReader(MediaKind.EPUB, file))
        assertFalse(shouldUseReadiumPdfReader(MediaKind.PDF, null))
    }

    @Test
    fun `invalid pdf does not enter Readium activity`() {
        val file = Files.createTempFile("readium-route-invalid", ".pdf").toFile().apply {
            writeText("not a pdf")
        }

        assertFalse(shouldUseReadiumPdfReader(MediaKind.PDF, file))
    }
}
