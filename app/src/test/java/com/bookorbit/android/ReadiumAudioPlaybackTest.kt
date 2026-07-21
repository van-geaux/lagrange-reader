package com.bookorbit.android

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.readium.r2.shared.util.mediatype.MediaType

class ReadiumAudioPlaybackTest {
    @Test
    fun supportedAudioExtensionsMapToReadiumMediaTypes() {
        assertEquals(MediaType.MP4, readiumAudioMediaType("book.m4b"))
        assertEquals(MediaType.MP4, readiumAudioMediaType("BOOK.M4A"))
        assertEquals(MediaType.MP4, readiumAudioMediaType("m4b"))
        assertEquals(MediaType.MP4, readiumAudioMediaType("audio/x-m4b"))
        assertEquals(MediaType.MP3, readiumAudioMediaType("book.mp3"))
        assertEquals(MediaType.MP3, readiumAudioMediaType("audio/mpeg"))
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

    @Test
    fun remoteAudioExtensionsMapToMedia3MimeTypes() {
        assertEquals(MimeTypes.AUDIO_MP4, media3AudioMimeType("book.m4b"))
        assertEquals(MimeTypes.AUDIO_MP4, media3AudioMimeType("BOOK.M4A"))
        assertEquals(MimeTypes.AUDIO_MPEG, media3AudioMimeType("book.mp3"))
        assertEquals(MimeTypes.AUDIO_FLAC, media3AudioMimeType("book.flac"))
        assertEquals(MimeTypes.AUDIO_OGG, media3AudioMimeType("book.ogg"))
        assertEquals(MimeTypes.AUDIO_OPUS, media3AudioMimeType("book.opus"))
        assertNull(media3AudioMimeType("book.epub"))
    }
}
