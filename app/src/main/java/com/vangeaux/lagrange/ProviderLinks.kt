package com.vangeaux.lagrange

import java.net.URLEncoder

private fun encodedSegments(value: String): String =
    value.split('/').joinToString("/") { segment ->
        URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
    }

internal data class ProviderLinkEntry(
    val key: String,
    val label: String,
    val urlBuilder: (String) -> String
)

internal val PROVIDER_LINK_REGISTRY: List<ProviderLinkEntry> = listOf(
    ProviderLinkEntry("google", "Google Books") { "https://books.google.com/books?id=${encodedSegments(it)}" },
    ProviderLinkEntry("goodreads", "Goodreads") { "https://www.goodreads.com/book/show/${encodedSegments(it)}" },
    ProviderLinkEntry("amazon", "Amazon") { "https://www.amazon.com/dp/${encodedSegments(it)}" },
    ProviderLinkEntry("hardcover", "Hardcover") { "https://hardcover.app/books/${encodedSegments(it)}" },
    ProviderLinkEntry("openLibrary", "Open Library") { "https://openlibrary.org/books/${encodedSegments(it)}" },
    ProviderLinkEntry("itunes", "Apple Books") { "https://books.apple.com/book/id${encodedSegments(it)}" },
    ProviderLinkEntry("audible", "Audible") { "https://www.audible.com/pd/${encodedSegments(it)}" },
    ProviderLinkEntry("librofm", "Libro.fm") { "https://libro.fm/audiobooks/${encodedSegments(it)}" },
    ProviderLinkEntry("kobo", "Kobo") { "https://www.kobo.com/ebook/${encodedSegments(it)}" },
    ProviderLinkEntry("lubimyczytac", "Lubimyczytac") { "https://lubimyczytac.pl/ksiazka/${encodedSegments(it)}" },
    ProviderLinkEntry("ranobedb", "RanobeDB") { "https://ranobedb.org/book/${encodedSegments(it)}" },
    ProviderLinkEntry("audnexus", "Audnexus") { "https://api.audnex.us/books/${encodedSegments(it)}" },
    ProviderLinkEntry("comicvine", "ComicVine") { "https://comicvine.gamespot.com/issue/${encodedSegments(it)}/" },
    ProviderLinkEntry("aladin", "Aladin") { "https://www.aladin.co.kr/shop/wproduct.aspx?ItemId=${encodedSegments(it)}" }
)

private val PROVIDER_LINK_REGISTRY_BY_KEY: Map<String, ProviderLinkEntry> =
    PROVIDER_LINK_REGISTRY.associateBy { it.key }

internal fun providerUrl(providerId: BookProviderId): String? =
    PROVIDER_LINK_REGISTRY_BY_KEY[providerId.provider]?.urlBuilder?.invoke(providerId.id)

internal fun providerDisplayName(provider: String): String =
    PROVIDER_LINK_REGISTRY_BY_KEY[provider]?.label ?: provider
