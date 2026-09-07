package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityLaunchStateTest {
    @Test
    fun `same-process activity recreation restores the browser start identity`() {
        assertEquals(
            "saved-start",
            browserStartIdentity(
                savedProcessIdentity = "process-1",
                savedStartIdentity = "saved-start",
                currentProcessIdentity = "process-1",
                newStartIdentity = "new-start"
            )
        )
    }

    @Test
    fun `process restart creates a new browser start identity`() {
        assertEquals(
            "new-start",
            browserStartIdentity(
                savedProcessIdentity = "process-1",
                savedStartIdentity = "saved-start",
                currentProcessIdentity = "process-2",
                newStartIdentity = "new-start"
            )
        )
    }

    @Test
    fun `missing saved state creates a new browser start identity`() {
        assertEquals(
            "new-start",
            browserStartIdentity(
                savedProcessIdentity = null,
                savedStartIdentity = null,
                currentProcessIdentity = "process-1",
                newStartIdentity = "new-start"
            )
        )
    }
}
