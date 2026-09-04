package com.vangeaux.lagrange

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class ComicPageImageViewerTest {
    @Test
    fun `page export title includes one based page number`() {
        assertEquals("My Comic - Page 3", comicPageExportTitle(" My Comic ", 2))
    }

    @Test
    fun `blank title gets stable comic fallback`() {
        assertEquals("comic - Page 1", comicPageExportTitle(" ", 0))
    }

    @Test
    fun `image scale is bounded`() {
        assertEquals(1f, boundedComicImageScale(0.5f), 0f)
        assertEquals(4f, boundedComicImageScale(8f), 0f)
    }

    @Test
    fun `image pan is bounded to the scaled image`() {
        assertEquals(
            Offset(100f, -100f),
            boundedComicImagePan(Offset(500f, -500f), 200f, 200f, 2f)
        )
    }

    @Test
    fun `paginated long press selects only a valid current page`() {
        assertEquals(2, paginatedComicLongPressPage(currentPage = 2, pageCount = 5))
        assertEquals(null, paginatedComicLongPressPage(currentPage = 5, pageCount = 5))
    }

    @Test
    fun `paginated page link resolves to its own reading order index`() {
        val readingOrder = listOf("cover.jpg", "page-2.jpg", "page-3.jpg")

        assertEquals(0, paginatedComicPageIndex("cover.jpg", readingOrder))
        assertEquals(2, paginatedComicPageIndex("page-3.jpg", readingOrder))
        assertEquals(null, paginatedComicPageIndex("missing.jpg", readingOrder))
    }

    @Test
    fun `portrait image is fitted to its painted bounds`() {
        assertEquals(
            Size(width = 400f, height = 800f),
            fittedReaderImageSize(
                containerWidth = 1000f,
                containerHeight = 800f,
                imageWidth = 1000,
                imageHeight = 2000
            )
        )
    }

    @Test
    fun `outside image tap is distinguished from inside image tap`() {
        assertEquals(
            true,
            isPointInsideTransformedReaderImage(
                point = Offset(300f, 400f),
                imageTopLeft = Offset(200f, 100f),
                imageSize = Size(400f, 600f),
                scale = 1f,
                pan = Offset.Zero
            )
        )
        assertEquals(
            false,
            isPointInsideTransformedReaderImage(
                point = Offset(100f, 400f),
                imageTopLeft = Offset(200f, 100f),
                imageSize = Size(400f, 600f),
                scale = 1f,
                pan = Offset.Zero
            )
        )
    }

    @Test
    fun `double tap toggles preset zoom and menu opens below press`() {
        assertEquals(2.5f, toggledReaderImageScale(1f), 0f)
        assertEquals(1f, toggledReaderImageScale(2.5f), 0f)
        assertEquals(Offset(120f, 228f), readerImageMenuAnchor(Offset(120f, 220f), 8f))
    }

    @Test
    fun `outside tap dismisses only when no transform is active`() {
        assertEquals(true, shouldDismissReaderImageViewerTap(false, false))
        assertEquals(false, shouldDismissReaderImageViewerTap(false, true))
        assertEquals(false, shouldDismissReaderImageViewerTap(true, false))
    }
}
