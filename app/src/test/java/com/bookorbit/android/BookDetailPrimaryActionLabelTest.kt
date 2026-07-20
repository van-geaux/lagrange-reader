package com.bookorbit.android

import org.junit.Assert.assertEquals
import org.junit.Test

class BookDetailPrimaryActionLabelTest {
    @Test
    fun `audiobooks use play while other books use read`() {
        val audiobook = BookSummary(
            libraryId = "library",
            id = "audio",
            fileId = "audio-file",
            title = "Audio",
            mediaKind = MediaKind.AUDIO
        )

        assertEquals("Play", bookDetailPrimaryActionLabel(audiobook))
        assertEquals("Read", bookDetailPrimaryActionLabel(audiobook.copy(mediaKind = MediaKind.EPUB)))
    }
}
