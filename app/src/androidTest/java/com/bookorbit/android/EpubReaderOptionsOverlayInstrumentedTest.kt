package com.bookorbit.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EpubReaderOptionsOverlayInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun optionsExposeOneCloseActionAndDismissFromVisibleBookArea() {
        val dismissCount = mutableIntStateOf(0)

        composeRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                EpubReaderDismissScrim(onDismiss = { dismissCount.intValue++ })
                EpubReaderOptionsHeader(
                    title = "Test Book",
                    status = "Chapter 1/2 · Page 3/4",
                    onClose = { dismissCount.intValue++ }
                )
            }
        }

        composeRule.onAllNodesWithText("Back").assertCountEquals(0)
        composeRule.onAllNodesWithText("Close").assertCountEquals(1)
        composeRule.onNodeWithText("Close").performClick()
        composeRule.onNodeWithContentDescription("Close reader options").performClick()

        composeRule.runOnIdle {
            assertEquals(2, dismissCount.intValue)
        }
    }
}
