package com.vangeaux.lagrange.provider.opds

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URI
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

internal sealed interface OpdsEntry {
    val id: String
    val title: String

    data class Navigation(override val id: String, override val title: String, val href: String) : OpdsEntry
    data class Publication(
        override val id: String,
        override val title: String,
        val author: String?,
        val publisher: String?,
        val summary: String?,
        val language: String?,
        val updated: String?,
        val coverUrl: String?,
        val categories: List<String>,
        val acquisitions: List<OpdsAcquisition>
    ) : OpdsEntry
}

internal data class OpdsAcquisition(val href: String, val mediaType: String?, val lengthBytes: Long?)

internal data class OpdsFeed(
    val title: String?,
    val selfUrl: String?,
    val nextUrl: String?,
    val totalResults: Int?,
    val entries: List<OpdsEntry>
)

/** Secure, provider-neutral OPDS 1 Atom parser and same-origin IRI resolver. */
internal object OpdsAtomParser {
    private const val ATOM_NS = "http://www.w3.org/2005/Atom"
    private const val DCTERMS_NS = "http://purl.org/dc/terms/"
    private const val DC_ELEMENTS_NS = "http://purl.org/dc/elements/1.1/"
    private const val OPENSEARCH_NS = "http://a9.com/-/spec/opensearch/1.1/"
    private const val ACQUISITION_REL = "http://opds-spec.org/acquisition"
    private const val IMAGE_REL = "http://opds-spec.org/image"
    private const val THUMBNAIL_REL = "http://opds-spec.org/image/thumbnail"
    private const val XML_BASE_NS = XMLConstants.XML_NS_URI

    fun parse(xml: String, requestUrl: String, trustedOrigin: String = requestUrl): OpdsFeed {
        val request = requireHttpUrl(requestUrl)
        val origin = requireHttpUrl(trustedOrigin)
        require(sameOrigin(request, origin)) { "OPDS request escaped the configured server origin" }

        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            isXIncludeAware = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
            setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
        }
        val root = factory.newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement
        require(root.localName == "feed" && root.namespaceURI == ATOM_NS) { "Expected an Atom feed" }

        val feedLinks = root.atomChildren("link")
        val parsedEntries = root.atomChildren("entry").mapNotNull { entry ->
            val links = entry.atomChildren("link")
            val acquisitions = links.mapNotNull link@{ link ->
                val rel = link.attribute("rel") ?: return@link null
                if (!isAcquisitionRelation(rel)) return@link null
                val href = trustedLink(link, requestUrl, origin) ?: return@link null
                OpdsAcquisition(
                    href = href,
                    mediaType = link.attribute("type")?.substringBefore(';')?.trim()?.takeIf(String::isNotEmpty),
                    lengthBytes = link.attribute("length")?.toLongOrNull()?.takeIf { it >= 0L }
                )
            }
            val title = entry.atomChildText("title")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val id = entry.atomChildText("id")?.takeIf(String::isNotBlank)
            val navigationLink = links.firstOrNull { link ->
                val rel = link.attribute("rel")
                rel.equals("subsection", ignoreCase = true) ||
                    rel.equals("http://opds-spec.org/subsection", ignoreCase = true) ||
                    (rel.isNullOrBlank() && link.attribute("type")
                        ?.contains("application/atom+xml", ignoreCase = true) == true &&
                        !link.attribute("type").orEmpty().contains("kind=acquisition", ignoreCase = true))
            }
            when {
                acquisitions.isNotEmpty() -> {
                    val cover = links.firstOrNull { link ->
                        val rel = link.attribute("rel")
                        rel.equals(IMAGE_REL, ignoreCase = true) || rel.equals(THUMBNAIL_REL, ignoreCase = true)
                    }?.let { trustedLink(it, requestUrl, origin) }
                    OpdsEntry.Publication(
                        id = id ?: acquisitions.first().href,
                        title = title,
                        author = entry.atomChildren("author").mapNotNull { it.atomChildText("name") }
                            .filter(String::isNotBlank).joinToString(", ").ifBlank { null },
                        publisher = entry.textChild(DCTERMS_NS, "publisher")
                            ?: entry.textChild(DC_ELEMENTS_NS, "publisher"),
                        summary = entry.atomChildText("summary")?.takeIf(String::isNotBlank)
                            ?: entry.atomChildText("content")?.takeIf(String::isNotBlank),
                        language = entry.textChild(DCTERMS_NS, "language")
                            ?: entry.textChild(DC_ELEMENTS_NS, "language"),
                        updated = entry.atomChildText("updated") ?: entry.atomChildText("published"),
                        coverUrl = cover,
                        categories = entry.atomChildren("category").mapNotNull { it.attribute("label") ?: it.attribute("term") }
                            .filter(String::isNotBlank),
                        acquisitions = acquisitions
                    )
                }
                navigationLink != null -> {
                    val href = trustedLink(navigationLink, requestUrl, origin) ?: return@mapNotNull null
                    OpdsEntry.Navigation(id ?: href, title, href)
                }
                else -> null
            }
        }
        val next = feedLinks.firstOrNull { it.attribute("rel").equals("next", ignoreCase = true) }
            ?.let { trustedLink(it, requestUrl, origin) }
        val self = feedLinks.firstOrNull { it.attribute("rel").equals("self", ignoreCase = true) }
            ?.let { trustedLink(it, requestUrl, origin) }
        val total = root.getElementsByTagNameNS(OPENSEARCH_NS, "totalResults").item(0)
            ?.textContent?.trim()?.toIntOrNull()?.takeIf { it >= 0 }
        return OpdsFeed(root.atomChildText("title")?.takeIf(String::isNotBlank), self, next, total, parsedEntries)
    }

    fun resolveTrustedUrl(requestUrl: String, href: String, trustedOrigin: String): String? = runCatching {
        val base = requireHttpUrl(requestUrl)
        val origin = requireHttpUrl(trustedOrigin)
        val reference = URI(href.trim())
        require(reference.userInfo == null && reference.fragment == null)
        val result = base.resolve(reference).normalize()
        require(sameOrigin(result, origin))
        result.toASCIIString()
    }.getOrNull()

    private fun trustedLink(link: Element, requestUrl: String, trustedOrigin: URI): String? {
        val href = link.attribute("href") ?: return null
        val effectiveBase = effectiveXmlBase(link, requestUrl) ?: return null
        return resolveTrustedUrl(effectiveBase, href, trustedOrigin.toASCIIString())
    }

    private fun effectiveXmlBase(element: Element, requestUrl: String): String? = runCatching {
        var base = URI(requestUrl)
        val ancestors = generateSequence(element as Node?) { it.parentNode }
            .filterIsInstance<Element>().toList().asReversed()
        ancestors.forEach { node ->
            node.getAttributeNS(XML_BASE_NS, "base").takeIf(String::isNotBlank)?.let { value ->
                base = base.resolve(URI(value.trim())).normalize()
            }
        }
        base.toASCIIString()
    }.getOrNull()

    private fun requireHttpUrl(value: String): URI {
        val uri = URI(value.trim())
        require(uri.scheme.equals("http", true) || uri.scheme.equals("https", true))
        require(!uri.host.isNullOrBlank() && uri.userInfo == null)
        return uri
    }

    private fun sameOrigin(first: URI, second: URI): Boolean =
        first.scheme.equals(second.scheme, true) && first.host.equals(second.host, true) &&
            effectivePort(first) == effectivePort(second)

    private fun effectivePort(uri: URI): Int = when {
        uri.port >= 0 -> uri.port
        uri.scheme.equals("https", true) -> 443
        else -> 80
    }

    private fun isAcquisitionRelation(rel: String): Boolean =
        rel.startsWith(ACQUISITION_REL, ignoreCase = true) ||
            rel.startsWith("https://opds-spec.org/acquisition", ignoreCase = true)

    private fun Element.atomChildren(name: String): List<Element> =
        (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
            .filter { it.localName == name && it.namespaceURI == ATOM_NS }

    private fun Element.atomChildText(name: String): String? =
        atomChildren(name).firstOrNull()?.textContent?.trim()?.takeIf(String::isNotEmpty)

    private fun Element.textChild(namespace: String, name: String): String? =
        (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
            .firstOrNull { it.localName == name && it.namespaceURI == namespace }
            ?.textContent?.trim()?.takeIf(String::isNotEmpty)

    private fun Element.attribute(name: String): String? =
        getAttribute(name).trim().takeIf(String::isNotEmpty)
}
