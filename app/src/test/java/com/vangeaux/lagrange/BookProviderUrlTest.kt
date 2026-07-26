package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookProviderUrlTest {
    @Test
    fun `builds urls for known providers`() {
        assertEquals(
            "https://books.google.com/books?id=abc123",
            providerUrl(BookProviderId("google", "abc123"))
        )
        assertEquals(
            "https://www.goodreads.com/book/show/456",
            providerUrl(BookProviderId("goodreads", "456"))
        )
        assertEquals(
            "https://www.amazon.com/dp/B00ABC123",
            providerUrl(BookProviderId("amazon", "B00ABC123"))
        )
        assertEquals(
            "https://hardcover.app/books/some-book",
            providerUrl(BookProviderId("hardcover", "some-book"))
        )
        assertEquals(
            "https://openlibrary.org/books/OL123M",
            providerUrl(BookProviderId("openLibrary", "OL123M"))
        )
        assertEquals(
            "https://books.apple.com/book/id123456",
            providerUrl(BookProviderId("itunes", "123456"))
        )
        assertEquals(
            "https://www.audible.com/pd/B0ABCDEF",
            providerUrl(BookProviderId("audible", "B0ABCDEF"))
        )
        assertEquals(
            "https://libro.fm/audiobooks/some-audiobook",
            providerUrl(BookProviderId("librofm", "some-audiobook"))
        )
        assertEquals(
            "https://www.kobo.com/ebook/some-ebook",
            providerUrl(BookProviderId("kobo", "some-ebook"))
        )
        assertEquals(
            "https://ranobedb.org/book/12345",
            providerUrl(BookProviderId("ranobedb", "12345"))
        )
        assertEquals(
            "https://api.audnex.us/books/B0ABCDEF",
            providerUrl(BookProviderId("audnexus", "B0ABCDEF"))
        )
        assertEquals(
            "https://comicvine.gamespot.com/issue/4000-123456/",
            providerUrl(BookProviderId("comicvine", "4000-123456"))
        )
        assertEquals(
            "https://www.aladin.co.kr/shop/wproduct.aspx?ItemId=123456789",
            providerUrl(BookProviderId("aladin", "123456789"))
        )
    }

    @Test
    fun `encodes slash segments for lubimyczytac`() {
        assertEquals(
            "https://lubimyczytac.pl/ksiazka/12345/tytul-ksiazki",
            providerUrl(BookProviderId("lubimyczytac", "12345/tytul-ksiazki"))
        )
    }

    @Test
    fun `encodes spaces and special characters in ids`() {
        assertEquals(
            "https://www.goodreads.com/book/show/456%20abc",
            providerUrl(BookProviderId("goodreads", "456 abc"))
        )
    }

    @Test
    fun `returns null for unsupported providers`() {
        assertNull(providerUrl(BookProviderId("unknown-provider", "abc")))
    }

    @Test
    fun `resolves display labels from the registry for known providers`() {
        assertEquals("Google Books", providerDisplayName("google"))
        assertEquals("Goodreads", providerDisplayName("goodreads"))
        assertEquals("Amazon", providerDisplayName("amazon"))
        assertEquals("Hardcover", providerDisplayName("hardcover"))
        assertEquals("Open Library", providerDisplayName("openLibrary"))
        assertEquals("Apple Books", providerDisplayName("itunes"))
        assertEquals("Audible", providerDisplayName("audible"))
        assertEquals("Libro.fm", providerDisplayName("librofm"))
        assertEquals("Kobo", providerDisplayName("kobo"))
        assertEquals("Lubimyczytac", providerDisplayName("lubimyczytac"))
        assertEquals("RanobeDB", providerDisplayName("ranobedb"))
        assertEquals("Audnexus", providerDisplayName("audnexus"))
        assertEquals("ComicVine", providerDisplayName("comicvine"))
        assertEquals("Aladin", providerDisplayName("aladin"))
    }

    @Test
    fun `falls back to the raw key for unsupported provider labels`() {
        assertEquals("unknown-provider", providerDisplayName("unknown-provider"))
    }
}
