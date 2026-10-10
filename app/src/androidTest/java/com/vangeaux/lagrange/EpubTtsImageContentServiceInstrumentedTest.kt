@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.vangeaux.lagrange

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Href
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

@RunWith(AndroidJUnit4::class)
class EpubTtsImageContentServiceInstrumentedTest {
    @Test
    fun imageTransformationSupportsDescriptionResourceBothAndNeither() {
        val image = image("Images/map.png?token=secret", "A map", "Alternate map text")

        assertNull(transformEpubTtsElement(image, EpubTtsImageSettings()))
        assertEquals(
            "A map",
            transformedCaption(image, EpubTtsImageSettings(readDescriptions = true))
        )
        assertEquals(
            "Resource: Images/map.png",
            transformedCaption(image, EpubTtsImageSettings(readResourceNames = true))
        )
        assertEquals(
            "A map. Resource: Images/map.png",
            transformedCaption(
                image,
                EpubTtsImageSettings(readDescriptions = true, readResourceNames = true)
            )
        )
    }

    @Test
    fun descriptionFallsBackToAltAndDuplicateResourceIsNotRepeated() {
        val altOnly = image("Images/figure.png", null, "Figure")
        assertEquals(
            "Figure",
            transformedCaption(altOnly, EpubTtsImageSettings(readDescriptions = true))
        )
        val filenameCaption = image(
            "Images/divider.gif",
            "Images/divider.gif",
            "A decorative divider"
        )
        assertEquals(
            "A decorative divider",
            transformedCaption(filenameCaption, EpubTtsImageSettings(readDescriptions = true))
        )
        val duplicate = image("Images/figure.png", "Images figure png", null)
        assertEquals(
            "Images figure png",
            transformedCaption(
                duplicate,
                EpubTtsImageSettings(readDescriptions = true, readResourceNames = true)
            )
        )
    }

    @Test
    fun iteratorSkipsDisabledImagesAndSafelyReversesPreparedLookahead() = runTest {
        val first = image("Images/first.png", "First", null)
        val second = image("Images/second.png", "Second", null)
        val iterator = EpubTtsImageIterator(
            FakeContentIterator(listOf(first, second)),
            EpubTtsImageSettings(readDescriptions = true)
        )

        assertTrue(iterator.hasNext())
        assertFalse(iterator.hasPrevious())
        assertTrue(iterator.hasNext())
        assertEquals("First", (iterator.next() as Content.ImageElement).caption)
        assertTrue(iterator.hasNext())
        assertTrue(iterator.hasPrevious())
        assertEquals("First", (iterator.previous() as Content.ImageElement).caption)
        assertEquals("First", (iterator.next() as Content.ImageElement).caption)

        val audio = Content.AudioElement(
            locator(),
            Link(href = requireNotNull(Href("audio/chapter.mp3")))
        )
        val skipping = EpubTtsImageIterator(
            FakeContentIterator(listOf(first, audio)),
            EpubTtsImageSettings()
        )
        assertTrue(skipping.hasNext())
        assertSame(audio, skipping.next())
        assertFalse(skipping.hasNext())
    }

    private fun transformedCaption(
        image: Content.ImageElement,
        settings: EpubTtsImageSettings
    ): String? = (transformEpubTtsElement(image, settings) as Content.ImageElement).caption

    private fun image(
        href: String,
        caption: String?,
        accessibilityLabel: String?
    ): Content.ImageElement = Content.ImageElement(
        locator = locator(),
        embeddedLink = Link(href = requireNotNull(Href(href))),
        caption = caption,
        attributes = accessibilityLabel?.let {
            listOf(Content.Attribute(Content.AttributeKey.ACCESSIBILITY_LABEL, it))
        }.orEmpty()
    )

    private fun locator() = Locator(
        href = requireNotNull(Url("chapter.xhtml")),
        mediaType = MediaType.XHTML
    )

    private class FakeContentIterator(
        private val elements: List<Content.Element>
    ) : Content.Iterator {
        private var index = 0

        override suspend fun hasNext(): Boolean = index < elements.size

        override fun next(): Content.Element {
            if (index >= elements.size) throw NoSuchElementException()
            return elements[index++]
        }

        override suspend fun nextOrNull(): Content.Element? =
            if (hasNext()) next() else null

        override suspend fun hasPrevious(): Boolean = index > 0

        override fun previous(): Content.Element {
            if (index <= 0) throw NoSuchElementException()
            return elements[--index]
        }

        override suspend fun previousOrNull(): Content.Element? =
            if (hasPrevious()) previous() else null
    }
}
