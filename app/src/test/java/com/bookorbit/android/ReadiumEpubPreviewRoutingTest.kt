package com.bookorbit.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadiumEpubPreviewRoutingTest {
    @Test
    fun onlyEpubPreviewUsesReadiumSpike() {
        assertTrue(shouldUseReadiumEpubPreview(MediaKind.EPUB, ReaderLaunchMode.PREVIEW))
        assertFalse(shouldUseReadiumEpubPreview(MediaKind.EPUB, ReaderLaunchMode.NORMAL))
        assertFalse(shouldUseReadiumEpubPreview(MediaKind.COMIC, ReaderLaunchMode.PREVIEW))
        assertFalse(shouldUseReadiumEpubPreview(MediaKind.AUDIO, ReaderLaunchMode.PREVIEW))
    }
}
