package com.vangeaux.lagrange

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class ContinuousComicReaderTest {
    @Test
    fun cacheBudgetUsesHalfTheAppHeapWithoutTheLegacyCeiling() {
        val mebibyte = 1024L * 1024L

        assertEquals(256L * mebibyte, continuousComicCacheBudgetBytes(512L * mebibyte).toLong())
        assertEquals(512L * mebibyte, continuousComicCacheBudgetBytes(1024L * mebibyte).toLong())
    }

    @Test
    fun `LTR edge taps keep previous on left and next on right`() {
        assertEquals(
            ContinuousComicTapAction.PREVIOUS,
            continuousComicTapAction(10f, 100f, LibraryReadingDirection.LEFT_TO_RIGHT)
        )
        assertEquals(
            ContinuousComicTapAction.NEXT,
            continuousComicTapAction(90f, 100f, LibraryReadingDirection.LEFT_TO_RIGHT)
        )
        assertEquals(
            ContinuousComicTapAction.MENU,
            continuousComicTapAction(50f, 100f, LibraryReadingDirection.LEFT_TO_RIGHT)
        )
    }

    @Test
    fun `RTL edge taps reverse logical navigation`() {
        assertEquals(
            ContinuousComicTapAction.NEXT,
            continuousComicTapAction(10f, 100f, LibraryReadingDirection.RIGHT_TO_LEFT)
        )
        assertEquals(
            ContinuousComicTapAction.PREVIOUS,
            continuousComicTapAction(90f, 100f, LibraryReadingDirection.RIGHT_TO_LEFT)
        )
    }

    @Test
    fun `continuous taps share vertical layouts and inversion with other readers`() {
        assertEquals(
            ContinuousComicTapAction.MENU,
            continuousComicTapAction(
                x = 50f,
                y = 10f,
                width = 100f,
                height = 100f,
                readingDirection = LibraryReadingDirection.LEFT_TO_RIGHT,
                layout = ReaderTapZoneLayout.KINDLE,
                invertMode = ReaderTapZoneInvertMode.NONE
            )
        )
        assertEquals(
            ContinuousComicTapAction.PREVIOUS,
            continuousComicTapAction(
                x = 10f,
                y = 10f,
                width = 100f,
                height = 100f,
                readingDirection = LibraryReadingDirection.LEFT_TO_RIGHT,
                layout = ReaderTapZoneLayout.KINDLE,
                invertMode = ReaderTapZoneInvertMode.VERTICAL
            )
        )
    }

    @Test
    fun `continuous page tap preserves vertical coordinates from its visible page`() {
        assertEquals(
            ContinuousComicTapAction.NEXT,
            continuousComicTapAction(
                position = Offset(50f, 90f),
                size = IntSize(100, 100),
                readingDirection = LibraryReadingDirection.LEFT_TO_RIGHT,
                layout = ReaderTapZoneLayout.VERTICAL_THIRDS,
                invertMode = ReaderTapZoneInvertMode.NONE
            )
        )
    }

    @Test
    fun `continuous page sampling honors width and decoded pixel bounds`() {
        assertEquals(4, continuousComicSampleSize(4000, 6000, 1000))
        assertEquals(2, continuousComicSampleSize(1000, 40000, 1000))
        assertEquals(1, continuousComicSampleSize(0, 0, 1000))
    }
}
