package com.vangeaux.lagrange

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import org.junit.Rule
import org.junit.Test
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertTrue

class AudiobookSeekConfirmationModalInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun outsideTapAndBackDoNotDismissConfirmation() {
        composeRule.setContent {
            MaterialTheme {
                AudiobookSeekConfirmationOverlay(
                    onKeepPosition = {},
                    onReturnToPrevious = {},
                    onDontShowAgainChange = {},
                    anchorBounds = IntRect(100, 500, 400, 800),
                    placement = AudiobookSeekConfirmationPlacement.OVER_ANCHOR
                )
            }
        }

        composeRule.onNodeWithTag("audiobook-seek-confirmation").assertIsDisplayed()
        composeRule.onNodeWithTag("audiobook-seek-confirmation-scrim").performClick()
        composeRule.onNodeWithTag("audiobook-seek-confirmation").assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithTag("audiobook-seek-confirmation").assertIsDisplayed()
    }

    @Test
    fun yesResolvesConfirmation() {
        var resolved by mutableStateOf(false)
        composeRule.setContent {
            MaterialTheme {
                AudiobookSeekConfirmationOverlay(
                    onKeepPosition = { resolved = true },
                    onReturnToPrevious = {},
                    onDontShowAgainChange = {},
                    anchorBounds = IntRect(100, 500, 400, 800),
                    placement = AudiobookSeekConfirmationPlacement.OVER_ANCHOR
                )
            }
        }

        composeRule.onNodeWithTag("audiobook-seek-confirmation-keep-position").performClick()
        assertTrue(resolved)
    }
}
