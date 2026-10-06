package com.vangeaux.lagrange

import com.vangeaux.lagrange.provider.komga.komgaDownloadProgress
import com.vangeaux.lagrange.provider.komga.komgaBookProjection
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KomgaDownloadContractTest {
    @Test
    fun `known response length reports bounded progress`() {
        assertEquals(0.25f, komgaDownloadProgress(25L, 100L))
        assertEquals(1f, komgaDownloadProgress(125L, 100L))
        assertEquals(0f, komgaDownloadProgress(-10L, 100L))
    }

    @Test
    fun `unknown response length leaves progress indeterminate`() {
        assertNull(komgaDownloadProgress(25L, null))
        assertNull(komgaDownloadProgress(25L, 0L))
    }

    @Test
    fun `generic archive MIME types still infer comic format from CBR and CBZ filenames`() {
        val cbz = komgaBookProjection(
            JSONObject(
                """
                {
                  "name":"One Piece.cbz",
                  "media": {
                    "mediaType":"application/zip"
                  }
                }
                """.trimIndent()
            )
        )
        val cbr = komgaBookProjection(
            JSONObject(
                """
                {
                  "name":"Berserk.cbr",
                  "media": {
                    "mediaType":"application/x-rar-compressed"
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals("cbz", cbz.format)
        assertEquals("cbr", cbr.format)
        assertTrue(cbz.mediaKind == MediaKind.COMIC)
        assertTrue(cbr.mediaKind == MediaKind.COMIC)
    }

    @Test
    fun `generic archive MIME types infer comics when Komga omits the filename`() {
        val cbz = komgaBookProjection(
            JSONObject("""{"media":{"mediaType":"application/zip"}}""")
        )
        val cbr = komgaBookProjection(
            JSONObject("""{"media":{"mediaType":"application/x-rar-compressed"}}""")
        )

        assertEquals("cbz", cbz.format)
        assertEquals("cbr", cbr.format)
        assertTrue(cbz.mediaKind == MediaKind.COMIC)
        assertTrue(cbr.mediaKind == MediaKind.COMIC)
    }

    @Test
    fun `filename extensions preserve EPUB and PDF format routing`() {
        val epub = komgaBookProjection(
            JSONObject("""{"name":"Novel.epub","media":{"mediaType":"application/octet-stream"}}""")
        )
        val pdf = komgaBookProjection(
            JSONObject("""{"name":"Manual.pdf","media":{"mediaType":"application/octet-stream"}}""")
        )

        assertEquals("epub", epub.format)
        assertEquals(MediaKind.EPUB, epub.mediaKind)
        assertEquals("pdf", pdf.format)
        assertEquals(MediaKind.PDF, pdf.mediaKind)
    }
}
