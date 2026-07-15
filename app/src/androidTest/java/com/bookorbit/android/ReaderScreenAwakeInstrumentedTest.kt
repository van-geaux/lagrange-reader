package com.bookorbit.android

import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReaderScreenAwakeInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun keepAwakeIsClearedWhenTheReaderLeavesComposition() {
        var readerVisible by mutableStateOf(true)
        var hostView: View? = null

        composeRule.setContent {
            hostView = LocalView.current
            if (readerVisible) KeepReaderScreenAwake()
        }

        composeRule.runOnIdle {
            assertTrue(hostView?.keepScreenOn == true)
            readerVisible = false
        }
        composeRule.runOnIdle {
            assertFalse(hostView?.keepScreenOn == true)
        }
    }
}
