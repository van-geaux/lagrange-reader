package com.bookorbit.android

import android.view.HapticFeedbackConstants
import org.junit.Assert.assertEquals
import org.junit.Test

class HapticFeedbackTest {
    @Test
    fun `Android 11 and newer use confirmation feedback`() {
        assertEquals(HapticFeedbackConstants.CONFIRM, hapticFeedbackConstantForSdk(30))
        assertEquals(HapticFeedbackConstants.CONFIRM, hapticFeedbackConstantForSdk(35))
    }

    @Test
    fun `older supported Android versions use virtual key feedback`() {
        assertEquals(HapticFeedbackConstants.VIRTUAL_KEY, hapticFeedbackConstantForSdk(26))
        assertEquals(HapticFeedbackConstants.VIRTUAL_KEY, hapticFeedbackConstantForSdk(29))
    }

    @Test
    fun `only enabling the app preference requests confirmation after the change`() {
        assertEquals(true, shouldConfirmHapticEnable(current = false, updated = true))
        assertEquals(false, shouldConfirmHapticEnable(current = true, updated = false))
        assertEquals(false, shouldConfirmHapticEnable(current = true, updated = true))
        assertEquals(false, shouldConfirmHapticEnable(current = false, updated = false))
    }
}
