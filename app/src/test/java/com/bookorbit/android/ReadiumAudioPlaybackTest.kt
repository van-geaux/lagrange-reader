package com.bookorbit.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.readium.r2.shared.util.mediatype.MediaType

class ReadiumAudioPlaybackTest {
    @Test
    fun supportedAudioExtensionsMapToReadiumMediaTypes() {
        assertEquals(MediaType.MP4, readiumAudioMediaType("book.m4b"))
        assertEquals(MediaType.MP4, readiumAudioMediaType("BOOK.M4A"))
        assertEquals(MediaType.MP3, readiumAudioMediaType("book.mp3"))
        assertEquals(MediaType.AAC, readiumAudioMediaType("book.aac"))
        assertEquals(MediaType.AIFF, readiumAudioMediaType("book.aiff"))
        assertEquals(MediaType.FLAC, readiumAudioMediaType("book.flac"))
        assertEquals(MediaType.OGG, readiumAudioMediaType("book.ogg"))
        assertEquals(MediaType.OPUS, readiumAudioMediaType("book.opus"))
        assertEquals(MediaType.WAV, readiumAudioMediaType("book.wav"))
        assertEquals(MediaType.WEBM_AUDIO, readiumAudioMediaType("book.webm"))
    }

    @Test
    fun nonAudioExtensionsAreRejected() {
        assertNull(readiumAudioMediaType("book.epub"))
        assertNull(readiumAudioMediaType("book.cbz"))
        assertNull(readiumAudioMediaType("book.pdf"))
        assertNull(readiumAudioMediaType("book"))
    }
}
