package com.bookorbit.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementPayloadParserTest {
    @Test
    fun `parseAchievements flattens categories and keeps unlock progress`() {
        val catalogue = BookOrbitPayloadParser.parseAchievements(
            """
                {
                  "categories": [
                    {
                      "key": "reading",
                      "label": "Reading",
                      "earnedCount": 1,
                      "totalCount": 2,
                      "achievements": [
                        {
                          "key": "first-book",
                          "category": "reading",
                          "name": "First Finish",
                          "description": "Finish a book",
                          "iconName": "book-check",
                          "rarity": "common",
                          "threshold": 1,
                          "hidden": false,
                          "sortOrder": 1,
                          "earned": true,
                          "awardedAt": "2026-07-17T12:30:00.000Z",
                          "currentProgress": 1
                        },
                        {
                          "key": "ten-books",
                          "category": "reading",
                          "name": "Page Turner",
                          "description": "Finish ten books",
                          "iconName": "books",
                          "rarity": "rare",
                          "threshold": 10,
                          "hidden": false,
                          "sortOrder": 2,
                          "earned": false,
                          "awardedAt": null,
                          "currentProgress": 4
                        }
                      ]
                    }
                  ],
                  "totalEarned": 1,
                  "totalAvailable": 2
                }
            """.trimIndent()
        )

        assertEquals(AchievementCatalogueStatus.AVAILABLE, catalogue.status)
        assertEquals(1, catalogue.totalEarned)
        assertEquals(2, catalogue.totalAvailable)
        assertEquals(listOf("first-book", "ten-books"), catalogue.items.map { it.key })
        assertTrue(catalogue.items.first().earned)
        assertEquals("2026-07-17T12:30:00.000Z", catalogue.items.first().awardedAt)
        assertFalse(catalogue.items.last().earned)
        assertEquals(4, catalogue.items.last().currentProgress)
        assertEquals(10, catalogue.items.last().threshold)
        assertEquals("Reading", catalogue.items.last().categoryLabel)
    }

    @Test
    fun `parseAchievements preserves censored secret achievement fields`() {
        val catalogue = BookOrbitPayloadParser.parseAchievements(
            """
                {
                  "categories": [{
                    "key": "exploration",
                    "label": "Exploration",
                    "achievements": [{
                      "key": "secret",
                      "category": "exploration",
                      "name": "???",
                      "description": "Secret Achievement",
                      "iconName": "help-circle",
                      "rarity": "epic",
                      "hidden": true,
                      "earned": false
                    }]
                  }]
                }
            """.trimIndent()
        )

        val secret = catalogue.items.single()
        assertTrue(secret.hidden)
        assertEquals("???", secret.name)
        assertEquals("Secret Achievement", secret.description)
        assertEquals(0, catalogue.totalEarned)
        assertEquals(1, catalogue.totalAvailable)
    }
}
