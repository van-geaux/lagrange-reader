package com.bookorbit.android

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthorPaginationTest {
    @Test
    fun `author catalog pagination loads every distinct page to the response total`() = runTest {
        val requests = mutableListOf<Int>()

        val catalog = loadCompleteAuthorCatalog { requestedPage ->
            requests += requestedPage
            when (requestedPage) {
                0 -> page(
                    page = 0,
                    total = 3,
                    items = listOf(author("alpha", "Alpha Author"), author("middle", "Middle Author"))
                )
                1 -> page(
                    page = 1,
                    total = 3,
                    items = listOf(author("zulu", "Zulu Author"))
                )
                else -> error("pagination should stop at the response total")
            }
        }

        assertEquals(listOf(0, 1), requests)
        assertEquals(listOf("alpha", "middle", "zulu"), catalog.items.map { it.id })
        assertEquals(3, catalog.total)
    }

    @Test
    fun `author catalog pagination stops when a page adds no distinct authors`() = runTest {
        val requests = mutableListOf<Int>()

        val catalog = loadCompleteAuthorCatalog { requestedPage ->
            requests += requestedPage
            if (requestedPage == 0) {
                page(
                    page = 0,
                    total = 4,
                    items = listOf(author("alpha", "Alpha Author"), author("middle", "Middle Author"))
                )
            } else {
                page(
                    page = 1,
                    total = 4,
                    items = listOf(author("middle", "Duplicate Middle"))
                )
            }
        }

        assertEquals(listOf(0, 1), requests)
        assertEquals(listOf("alpha", "middle"), catalog.items.map { it.id })
    }

    @Test
    fun `author jump targets expose only represented initials`() {
        val targets = buildAuthorJumpTargets(
            listOf(
                author("numeric", "123 Author"),
                author("alpha", "Alpha Author"),
                author("middle", "Middle Author"),
                author("zulu", "Zulu Author")
            )
        )

        assertEquals(listOf('#', 'A', 'M', 'Z'), targets.map { it.first })
        assertEquals(listOf(0, 1, 2, 3), targets.map { it.second })
    }

    private fun page(
        page: Int,
        total: Int,
        items: List<AuthorSummary>
    ) = AuthorCatalogPage(
        items = items,
        total = total,
        page = page,
        size = 2
    )

    private fun author(id: String, name: String) = AuthorSummary(id = id, name = name)
}
