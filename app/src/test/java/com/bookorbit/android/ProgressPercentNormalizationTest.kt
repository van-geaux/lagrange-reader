package com.bookorbit.android

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressPercentNormalizationTest {
    @Test
    fun `normalizeStoredProgressPercent scales fractional values to percent`() {
        assertEquals(37.5f, normalizeStoredProgressPercent(0.375f))
        assertEquals(50f, normalizeStoredProgressPercent(50f))
        assertEquals(100f, normalizeStoredProgressPercent(120f))
    }
}
