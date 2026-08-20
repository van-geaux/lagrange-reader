package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverExportTest {
    @Test
    fun `cover filename removes filesystem separators and preserves readable title`() {
        assertEquals(
            "A Book_ Volume 2",
            sanitizeCoverFileName(" A Book: Volume 2 ")
        )
    }

    @Test
    fun `blank cover title falls back to cover`() {
        assertEquals("cover", sanitizeCoverFileName("   "))
    }

    @Test
    fun `cover filename is bounded`() {
        assertEquals(120, sanitizeCoverFileName("x".repeat(200)).length)
    }
}
