package com.vangeaux.lagrange.provider.opds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

class OpdsAtomParserTest {
    @Test
    fun `parses mixed navigation and acquisition entries without conflating them`() {
        val parsed = OpdsAtomParser.parse(
            xml = """
                <feed xmlns="http://www.w3.org/2005/Atom"
                      xmlns:dc="http://purl.org/dc/terms/">
                  <title>Library</title>
                  <link rel="next" href="page/2" />
                  <entry>
                    <title>Fiction</title>
                    <link rel="subsection" type="application/atom+xml;profile=opds-catalog;kind=navigation" href="fiction" />
                  </entry>
                  <entry>
                    <id>urn:book:1</id>
                    <title>A Book</title>
                    <author><name>A. Writer</name></author>
                    <dc:language>en</dc:language>
                    <link rel="http://opds-spec.org/image" type="image/jpeg" href="covers/1.jpg" />
                    <link rel="http://opds-spec.org/acquisition/open-access" type="application/epub+zip" length="1234" href="downloads/1.epub" />
                  </entry>
                </feed>
            """.trimIndent(),
            requestUrl = "https://books.example/library/opds/root",
            trustedOrigin = "https://books.example/library/opds/root"
        )

        assertEquals("Library", parsed.title)
        assertEquals("https://books.example/library/opds/page/2", parsed.nextUrl)
        assertEquals(2, parsed.entries.size)
        val navigation = parsed.entries.single { it is OpdsEntry.Navigation } as OpdsEntry.Navigation
        assertEquals("Fiction", navigation.title)
        assertEquals("https://books.example/library/opds/fiction", navigation.href)
        val book = parsed.entries.single { it is OpdsEntry.Publication } as OpdsEntry.Publication
        assertEquals("urn:book:1", book.id)
        assertEquals("A. Writer", book.author)
        assertEquals("en", book.language)
        assertEquals("https://books.example/library/opds/covers/1.jpg", book.coverUrl)
        assertEquals("https://books.example/library/opds/downloads/1.epub", book.acquisitions.single().href)
        assertEquals("application/epub+zip", book.acquisitions.single().mediaType)
        assertEquals(1234L, book.acquisitions.single().lengthBytes)
    }

    @Test
    fun `uses feed provided links and preserves configured origin across reverse proxy prefix`() {
        assertEquals(
            "https://books.example/proxy/opds/next?page=4",
            OpdsAtomParser.resolveTrustedUrl(
                "https://books.example/proxy/opds/root",
                "next?page=4",
                "https://books.example/proxy/opds/root"
            )
        )
        assertNull(
            OpdsAtomParser.resolveTrustedUrl(
                "https://books.example/proxy/opds/root",
                "https://attacker.example/download.epub",
                "https://books.example/proxy/opds/root"
            )
        )
        assertNull(
            OpdsAtomParser.resolveTrustedUrl(
                "https://books.example/proxy/opds/root",
                "//attacker.example/download.epub",
                "https://books.example/proxy/opds/root"
            )
        )
        assertNull(
            OpdsAtomParser.resolveTrustedUrl(
                "https://books.example/proxy/opds/root",
                "javascript:alert(1)",
                "https://books.example/proxy/opds/root"
            )
        )
    }

    @Test
    fun `does not expose external-origin navigation acquisition or artwork links`() {
        val parsed = OpdsAtomParser.parse(
            xml = """
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <entry><title>Escape</title><link rel="subsection" href="https://evil.example/feed" /></entry>
                  <entry>
                    <id>urn:book:2</id><title>Unsafe links</title>
                    <link rel="http://opds-spec.org/image" href="//evil.example/cover" />
                    <link rel="http://opds-spec.org/acquisition" href="https://evil.example/book.epub" />
                  </entry>
                </feed>
            """.trimIndent(),
            requestUrl = "https://books.example/opds",
            trustedOrigin = "https://books.example/opds"
        )

        assertTrue(parsed.entries.isEmpty())
        assertNull(parsed.nextUrl)
    }

    @Test
    fun `ignores entries without usable navigation or acquisition links`() {
        val parsed = OpdsAtomParser.parse(
            xml = """
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <entry><id>urn:metadata-only</id><title>Metadata only</title></entry>
                  <entry><title>Bad target</title><link rel="subsection" href="javascript:alert(1)" /></entry>
                </feed>
            """.trimIndent(),
            requestUrl = "https://books.example/opds",
            trustedOrigin = "https://books.example/opds"
        )

        assertFalse(parsed.entries.any { it is OpdsEntry.Publication })
        assertTrue(parsed.entries.isEmpty())
    }

    @Test
    fun `parses when the platform factory rejects optional JAXP security settings`() {
        val url = "https://books.example/opds"
        val parsed = OpdsAtomParser.parseWithFactory(
            xml = """
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title>Android-compatible catalog</title>
                </feed>
            """.trimIndent(),
            requestUrl = url,
            trustedOrigin = url,
            factory = UnsupportedJaxpSettingsFactory(),
            supportsJaxpSecurityOptions = false
        )

        assertEquals("Android-compatible catalog", parsed.title)
    }
}

private class UnsupportedJaxpSettingsFactory : DocumentBuilderFactory() {
    private val delegate = DocumentBuilderFactory.newInstance()

    override fun newDocumentBuilder(): DocumentBuilder {
        delegate.isNamespaceAware = isNamespaceAware
        return delegate.newDocumentBuilder()
    }

    override fun setAttribute(name: String?, value: Any?) {
        throw IllegalArgumentException(name)
    }

    override fun getAttribute(name: String?): Any? = throw IllegalArgumentException(name)

    override fun setFeature(name: String?, value: Boolean) {
        throw ParserConfigurationException(name)
    }

    override fun getFeature(name: String?): Boolean = throw ParserConfigurationException(name)
}
