@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.vangeaux.lagrange

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.util.Language

@RunWith(AndroidJUnit4::class)
class EpubTtsSentenceTokenizerInstrumentedTest {
    @Test
    fun AndroidICURepairsAbbreviationBoundariesForSupportedEnglishMetadata() {
        val text = "At the seminar, Dr. Alfred Lanning of U.S. Robots demonstrated a machine."
        val cases = listOf(
            EpubTtsSentenceTokenizer(language = Language("en")),
            EpubTtsSentenceTokenizer(language = Language("English")),
            EpubTtsSentenceTokenizer(
                language = null,
                fallbackLanguage = Language("English")
            )
        )

        cases.forEach { tokenizer ->
            val actual = tokenizer.tokenize(text).map { text.substring(it).trim() }
            assertEquals(listOf(text), actual)
        }
    }

    @Test
    fun explicitNonEnglishContentLanguageOverridesEnglishPublicationFallback() {
        val text = "Dr. Alfred Lanning spoke."
        val expected = EpubTtsSentenceTokenizer(Language("de"))
            .tokenize(text)
            .map { text.substring(it).trim() }
        val actual = EpubTtsSentenceTokenizer(
            language = Language("de"),
            fallbackLanguage = Language("English")
        ).tokenize(text).map { text.substring(it).trim() }

        assertEquals(expected, actual)
    }

    @Test
    fun AndroidICUPreservesAGenuineSentenceBoundaryAfterAnAmbiguousTitle() {
        val text = "He met the Dr. Then he left."
        val actual = EpubTtsSentenceTokenizer(
            language = null,
            fallbackLanguage = Language("English")
        ).tokenize(text).map { text.substring(it).trim() }

        assertEquals(listOf("He met the Dr.", "Then he left."), actual)
    }
}
