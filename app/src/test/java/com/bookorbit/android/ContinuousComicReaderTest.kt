package com.bookorbit.android

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
    fun `continuous page sampling honors width and decoded pixel bounds`() {
        assertEquals(4, continuousComicSampleSize(4000, 6000, 1000))
        assertEquals(2, continuousComicSampleSize(1000, 40000, 1000))
        assertEquals(1, continuousComicSampleSize(0, 0, 1000))
    }
}
