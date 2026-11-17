package com.vangeaux.lagrange

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CoverAspectLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mixedBookAndSeriesCoversHaveCorrectShapesAndAlignedBottoms() {
        val portrait = BookSummary("books", "portrait", null, "Portrait")
        val square = BookSummary("audio", "square", null, "Square", coverAspectRatio = CoverAspectRatio.SQUARE)
        composeRule.setContent {
            BookOrbitTheme {
                Column {
                    Row {
                        Box(Modifier.width(100.dp)) { BookCardCoverSlot(portrait, { null }) }
                        Box(Modifier.width(100.dp)) { BookCardCoverSlot(square, { null }) }
                    }
                    Row {
                        Box(Modifier.width(100.dp)) {
                            SeriesCatalogCover(null, "portrait series", { null }, CoverAspectRatio.PORTRAIT)
                        }
                        Box(Modifier.width(100.dp)) {
                            SeriesCatalogCover(null, "square series", { null }, CoverAspectRatio.SQUARE)
                        }
                    }
                }
            }
        }

        listOf(
            "book-cover-portrait" to "book-cover-square",
            "series-cover-portrait series" to "series-cover-square series"
        ).forEach { (portraitTag, squareTag) ->
            val portraitBounds = composeRule.onNodeWithTag(portraitTag).getUnclippedBoundsInRoot()
            val squareBounds = composeRule.onNodeWithTag(squareTag).getUnclippedBoundsInRoot()
            assertEquals(
                2f / 3f,
                (portraitBounds.right - portraitBounds.left) / (portraitBounds.bottom - portraitBounds.top),
                0.02f
            )
            assertEquals(
                1f,
                (squareBounds.right - squareBounds.left) / (squareBounds.bottom - squareBounds.top),
                0.02f
            )
            assertEquals(portraitBounds.bottom.value, squareBounds.bottom.value, 0.5f)
        }
    }
}
