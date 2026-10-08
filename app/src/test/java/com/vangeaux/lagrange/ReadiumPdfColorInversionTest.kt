package com.vangeaux.lagrange

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class ReadiumPdfColorInversionTest {
    @Test
    fun `PDF color inversion uses a full RGB inversion matrix`() {
        assertArrayEquals(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            ),
            pdfColorMatrix(inverted = true),
            0f
        )
    }

    @Test
    fun `PDF color inversion disabled uses the identity matrix`() {
        assertArrayEquals(
            floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ),
            pdfColorMatrix(inverted = false),
            0f
        )
    }
}