package com.vangeaux.lagrange

import com.vangeaux.lagrange.provider.kavita.kavitaDashboardItems
import com.vangeaux.lagrange.provider.kavita.kavitaSeriesPageItems
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class KavitaHomeShelfTest {
    @Test
    fun dashboardParserAcceptsPagedItemsEnvelope() {
        val items = kavitaDashboardItems(
            """
            {"items":[{"id":42,"name":"A Series"}],"totalCount":1}
            """
        )

        assertEquals(1, items.size)
        assertEquals(42, items.single().optInt("id"))
    }

    @Test
    fun dashboardParserAcceptsRawArray() {
        val items = kavitaDashboardItems(
            """
            [{"seriesId":42,"count":3}]
            """
        )

        assertEquals(1, items.size)
        assertEquals(42, items.single().optInt("seriesId"))
    }

    @Test
    fun catalogParserAcceptsPagedItemsEnvelope() {
        val items = kavitaSeriesPageItems("{\"items\":[{\"id\":42}]}")

        assertEquals(1, items.length())
        assertEquals(42, items.optJSONObject(0)?.optInt("id"))
    }
}
