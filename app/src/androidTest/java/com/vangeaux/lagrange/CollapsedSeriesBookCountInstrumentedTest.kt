package com.vangeaux.lagrange

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class CollapsedSeriesBookCountInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun seriesBookCountSubstitutesTheCountForPluralAndSingularLabels() {
        composeRule.setContent {
            Column {
                Text(seriesBookCountLabel(5))
                Text(seriesBookCountLabel(1))
            }
        }

        composeRule.onNodeWithText("5 books").assertIsDisplayed()
        composeRule.onNodeWithText("1 book").assertIsDisplayed()
    }
}
