package com.bookorbit.android

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import org.junit.Assert.assertEquals
import org.junit.Test

class AchievementIconTest {
    @Test
    fun `server icon names select semantic Android symbols with a safe fallback`() {
        assertEquals(Icons.Default.MenuBook, achievementIcon("book-open"))
        assertEquals(Icons.Default.LocalLibrary, achievementIcon("library"))
        assertEquals(Icons.Default.Palette, achievementIcon("palette"))
        assertEquals(Icons.Default.EmojiEvents, achievementIcon("unknown-future-icon"))
    }
}
