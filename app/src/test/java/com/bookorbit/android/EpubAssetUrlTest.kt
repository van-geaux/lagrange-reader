package com.bookorbit.android

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EpubAssetUrlTest {
    @Test
    fun `chapter base url preserves nested paths and encodes spaces`() {
        val root = File("reader cache/epub").absoluteFile
        val chapter = File(root, "OEBPS/Text Part/chapter.xhtml")

        assertEquals(
            "https://appassets.androidplatform.net/epub/OEBPS/Text%20Part/",
            epubChapterBaseUrl(root, chapter)
        )
    }

    @Test
    fun `chapter base url rejects files outside the extracted root`() {
        val root = File("reader-cache/epub/OEBPS").absoluteFile
        val chapter = File(root.parentFile, "outside.xhtml")

        assertThrows(IllegalArgumentException::class.java) {
            epubChapterBaseUrl(root, chapter)
        }
    }
}
