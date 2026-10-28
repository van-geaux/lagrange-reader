@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.vangeaux.lagrange

import com.ibm.icu.text.BreakIterator
import com.ibm.icu.util.ULocale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.readium.r2.shared.util.Language
import org.readium.r2.shared.util.tokenizer.Tokenizer

class EpubTtsSentenceTokenizerTest {
    @Test
    fun `merges the false Readium boundary after Doctor before a name`() {
        val text = "Dr. Alfred Lanning looked up."

        assertEquals(
            listOf(0..text.lastIndex),
            mergeEpubTtsSentenceRanges(text, listOf(0..3, 4..text.lastIndex))
        )
    }

    @Test
    fun `merges documented abbreviation boundaries and chained titles`() {
        val cases = listOf(
            MergeCase(
                raw = listOf("Prof. ", "Dr. ", "Smith spoke."),
                expected = listOf("Prof. Dr. Smith spoke.")
            ),
            MergeCase(
                raw = listOf("Lt. ", "Col. ", "Smith saluted."),
                expected = listOf("Lt. Col. Smith saluted.")
            ),
            MergeCase(
                raw = listOf("Mt. ", "Everest is high."),
                expected = listOf("Mt. Everest is high.")
            ),
            MergeCase(
                raw = listOf("Visit St. ", "Paul's today."),
                expected = listOf("Visit St. Paul's today.")
            ),
            MergeCase(
                raw = listOf("He worked for U.S. ", "Robots."),
                expected = listOf("He worked for U.S. Robots.")
            ),
            MergeCase(
                raw = listOf("Fruit, e.g. ", "Apples, is good."),
                expected = listOf("Fruit, e.g. Apples, is good.")
            ),
            MergeCase(
                raw = listOf("The term, i.e. ", "Robot, is defined."),
                expected = listOf("The term, i.e. Robot, is defined.")
            ),
            MergeCase(
                raw = listOf("Mr. and Mrs. ", "Smith came."),
                expected = listOf("Mr. and Mrs. Smith came.")
            ),
            MergeCase(
                raw = listOf("He lives at No. ", "5 Downing Street."),
                expected = listOf("He lives at No. 5 Downing Street.")
            ),
            MergeCase(
                raw = listOf("See vol. ", "IV for more."),
                expected = listOf("See vol. IV for more.")
            ),
            MergeCase(
                raw = listOf("J. ", "R. ", "R. ", "Tolkien wrote it."),
                expected = listOf("J. R. R. Tolkien wrote it.")
            ),
            MergeCase(
                raw = listOf("Flt. ", "Lt. ", "Smith flew."),
                expected = listOf("Flt. Lt. Smith flew.")
            ),
            MergeCase(
                raw = listOf("Dr. ", "A. ", "Smith spoke."),
                expected = listOf("Dr. A. Smith spoke.")
            )
        )

        cases.forEach(::assertMerged)
    }

    @Test
    fun `preserves genuine sentence endings after ambiguous abbreviations`() {
        val cases = listOf(
            MergeCase(
                raw = listOf("He met the Dr. ", "Then he left."),
                expected = listOf("He met the Dr. ", "Then he left.")
            ),
            MergeCase(
                raw = listOf("Baker St. ", "The house was dark."),
                expected = listOf("Baker St. ", "The house was dark.")
            ),
            MergeCase(
                raw = listOf("He moved to the U.S. ", "Then he wrote."),
                expected = listOf("He moved to the U.S. ", "Then he wrote.")
            ),
            MergeCase(
                raw = listOf("He said no. ", "Then he left."),
                expected = listOf("He said no. ", "Then he left.")
            ),
            MergeCase(
                raw = listOf("It was Plan A. ", "Then it failed."),
                expected = listOf("It was Plan A. ", "Then it failed.")
            ),
            MergeCase(
                raw = listOf("We sold apples, pears, etc. ", "Then we closed."),
                expected = listOf("We sold apples, pears, etc. ", "Then we closed.")
            ),
            MergeCase(
                raw = listOf("Meet at 5 p.m. ", "Friday is fine."),
                expected = listOf("Meet at 5 p.m. ", "Friday is fine.")
            ),
            MergeCase(
                raw = listOf("\"Ask the Dr.\" ", "He left."),
                expected = listOf("\"Ask the Dr.\" ", "He left.")
            ),
            MergeCase(
                raw = listOf("Smith & Co. ", "Ltd. ", "Board met."),
                expected = listOf("Smith & Co. Ltd. ", "Board met.")
            ),
            MergeCase(
                raw = listOf("Ask the (Dr.) ", "He left."),
                expected = listOf("Ask the (Dr.) ", "He left.")
            )
        )

        cases.forEach(::assertMerged)
    }

    @Test
    fun `merges lower case dialogue tags but not a following proper name`() {
        listOf(
            MergeCase(
                raw = listOf("\"Where are you?\" ", "he asked."),
                expected = listOf("\"Where are you?\" he asked.")
            ),
            MergeCase(
                raw = listOf("\"Go!\" ", "she shouted."),
                expected = listOf("\"Go!\" she shouted.")
            ),
            MergeCase(
                raw = listOf("\u201cGo!\u201d ", "she shouted."),
                expected = listOf("\u201cGo!\u201d she shouted.")
            ),
            MergeCase(
                raw = listOf("{\"Go!\"} ", "she shouted."),
                expected = listOf("{\"Go!\"} she shouted.")
            ),
            MergeCase(
                raw = listOf("\"Where are you?\" ", "Powell asked."),
                expected = listOf("\"Where are you?\" ", "Powell asked.")
            )
        ).forEach(::assertMerged)
    }

    @Test
    fun `case sensitive groups distinguish name prefixes from reference forms`() {
        listOf(
            MergeCase(
                raw = listOf("Sec. ", "Smith spoke."),
                expected = listOf("Sec. Smith spoke.")
            ),
            MergeCase(
                raw = listOf("See sec. ", "Smith for details."),
                expected = listOf("See sec. ", "Smith for details.")
            ),
            MergeCase(
                raw = listOf("See sec. ", "12 for details."),
                expected = listOf("See sec. 12 for details.")
            )
        ).forEach(::assertMerged)
    }

    @Test
    fun `English tokenizer merges the delegate sentence ranges`() {
        val text = "Dr. Alfred Lanning looked up."
        val delegate = FixedRangeTokenizer(listOf(0..3, 4..text.lastIndex))

        val ranges = EpubTtsSentenceTokenizer(Language("en-GB"), delegate).tokenize(text)

        assertEquals(listOf(0..text.lastIndex), ranges)
    }

    @Test
    fun `ISO 639 three letter English tag applies the English rules`() {
        val text = "Dr. Alfred Lanning looked up."
        val delegate = FixedRangeTokenizer(listOf(0..2, 4..text.lastIndex))

        val ranges = EpubTtsSentenceTokenizer(Language("eng"), delegate).tokenize(text)

        assertEquals(listOf(0..text.lastIndex), ranges)
    }

    @Test
    fun `English language name from EPUB metadata applies the English rules`() {
        val pieces = listOf(
            "At the seminar, Dr. ",
            "Alfred Lanning of U.S. ",
            "Robots demonstrated a machine."
        )
        val text = pieces.joinToString("")
        val delegate = FixedRangeTokenizer(rangesFor(text, pieces))

        val ranges = EpubTtsSentenceTokenizer(Language("English"), delegate).tokenize(text)

        assertEquals(listOf(0..text.lastIndex), ranges)
    }

    @Test
    fun `English language name is canonicalized for the ICU delegate`() {
        assertEquals("en", normalizeEpubTtsSentenceLanguage(Language("English"))?.code)
        assertEquals("en-GB", normalizeEpubTtsSentenceLanguage(Language("en-GB"))?.code)
        assertEquals("de", normalizeEpubTtsSentenceLanguage(Language("de"))?.code)
        assertEquals(null, normalizeEpubTtsSentenceLanguage(null))
    }

    @Test
    fun `missing content language falls back to the publication language`() {
        val pieces = listOf("Dr. ", "Alfred Lanning of U.S. ", "Robots spoke.")
        val text = pieces.joinToString("")
        val delegate = FixedRangeTokenizer(rangesFor(text, pieces))

        val ranges = EpubTtsSentenceTokenizer(
            language = null,
            fallbackLanguage = Language("English"),
            delegate = delegate
        ).tokenize(text)

        assertEquals(listOf(0..text.lastIndex), ranges)
    }

    @Test
    fun `explicit non English content language overrides the publication language`() {
        val pieces = listOf("Dr. ", "Alfred Lanning spoke.")
        val text = pieces.joinToString("")
        val delegateRanges = rangesFor(text, pieces)

        val ranges = EpubTtsSentenceTokenizer(
            language = Language("de"),
            fallbackLanguage = Language("English"),
            delegate = FixedRangeTokenizer(delegateRanges)
        ).tokenize(text)

        assertEquals(delegateRanges, ranges)
    }

    @Test
    fun `non English tokenizer preserves the delegate sentence ranges`() {
        val text = "Dr. Alfred Lanning looked up."
        val delegateRanges = listOf(0..3, 4..text.lastIndex)
        val delegate = FixedRangeTokenizer(delegateRanges)

        val ranges = EpubTtsSentenceTokenizer(Language("de"), delegate).tokenize(text)

        assertEquals(delegateRanges, ranges)
    }

    @Test
    fun `unknown language preserves the delegate sentence ranges`() {
        val text = "Dr. Alfred Lanning looked up."
        val delegateRanges = listOf(0..3, 4..text.lastIndex)

        val ranges = EpubTtsSentenceTokenizer(
            language = null,
            delegate = FixedRangeTokenizer(delegateRanges)
        ).tokenize(text)

        assertEquals(delegateRanges, ranges)
    }

    @Test
    fun `merges the documented ICU4J sentence boundary fixtures`() {
        val cases = listOf(
            IcuCase("Dr. Alfred Lanning looked up.", listOf("Dr. Alfred Lanning looked up.")),
            IcuCase("He met the Dr. Then he left.", listOf("He met the Dr.", "Then he left.")),
            IcuCase("Prof. Dr. Smith spoke.", listOf("Prof. Dr. Smith spoke.")),
            IcuCase("Lt. Col. Smith saluted.", listOf("Lt. Col. Smith saluted.")),
            IcuCase("Mt. Everest is high.", listOf("Mt. Everest is high.")),
            IcuCase("Visit St. Paul's today.", listOf("Visit St. Paul's today.")),
            IcuCase("Baker St. The house was dark.", listOf("Baker St.", "The house was dark.")),
            IcuCase("He worked for U.S. Robots.", listOf("He worked for U.S. Robots.")),
            IcuCase("He moved to the U.S. Then he wrote.", listOf("He moved to the U.S.", "Then he wrote.")),
            IcuCase("Fruit, e.g. Apples, is good.", listOf("Fruit, e.g. Apples, is good.")),
            IcuCase("Mr. and Mrs. Smith came.", listOf("Mr. and Mrs. Smith came.")),
            IcuCase("He lives at No. 5 Downing Street.", listOf("He lives at No. 5 Downing Street.")),
            IcuCase("He said no. Then he left.", listOf("He said no.", "Then he left.")),
            IcuCase("The fire was lit. Then we ate.", listOf("The fire was lit.", "Then we ate.")),
            IcuCase("J. R. R. Tolkien wrote it.", listOf("J. R. R. Tolkien wrote it.")),
            IcuCase("It was Plan A. Then it failed.", listOf("It was Plan A.", "Then it failed.")),
            IcuCase(
                "We sold apples, pears, etc. Then we closed.",
                listOf("We sold apples, pears, etc.", "Then we closed.")
            ),
            IcuCase("Meet at 5 p.m. Friday is fine.", listOf("Meet at 5 p.m.", "Friday is fine.")),
            IcuCase("\"Where are you?\" he asked.", listOf("\"Where are you?\" he asked.")),
            IcuCase("\"Go!\" she shouted.", listOf("\"Go!\" she shouted.")),
            IcuCase(
                "\"Where are you?\" Powell asked.",
                listOf("\"Where are you?\"", "Powell asked.")
            ),
            IcuCase("Flt. Lt. Smith flew.", listOf("Flt. Lt. Smith flew.")),
            IcuCase("Dr. A. Smith spoke.", listOf("Dr. A. Smith spoke.")),
            IcuCase("Ask for Dr. Who in Room 5.", listOf("Ask for Dr. Who in Room 5.")),
            IcuCase("\"Ask the Dr.\" He left.", listOf("\"Ask the Dr.\"", "He left.")),
            IcuCase("See vol. IV for more.", listOf("See vol. IV for more.")),
            IcuCase("Smith & Co. Ltd. Board met.", listOf("Smith & Co. Ltd.", "Board met."))
        )

        cases.forEach { case ->
            val ranges = mergeEpubTtsSentenceRanges(case.text, icuSentenceRanges(case.text))
            val actual = ranges.map { case.text.substring(it).trim() }
            assertEquals("Unexpected ICU merge for: ${case.text}", case.expected, actual)
        }
    }

    @Test(timeout = 3_000)
    fun `long initial chains within Android speech limits merge efficiently`() {
        val initialCount = 1_000
        val text = buildString {
            repeat(initialCount) { append("A. ") }
            append("Smith spoke.")
        }
        val ranges = buildList {
            repeat(initialCount) { index ->
                val start = index * 3
                add(start..start + 1)
            }
            add(initialCount * 3..text.lastIndex)
        }

        assertEquals(listOf(0..text.lastIndex), mergeEpubTtsSentenceRanges(text, ranges))
    }

    private fun assertMerged(case: MergeCase) {
        val text = case.raw.joinToString("")
        val ranges = rangesFor(text, case.raw)
        val actual = mergeEpubTtsSentenceRanges(text, ranges).map(text::substring)
        assertEquals("Unexpected merge for: $text", case.expected, actual)
    }

    private fun rangesFor(text: String, pieces: List<String>): List<IntRange> {
        var start = 0
        return pieces.map { piece ->
            require(text.startsWith(piece, start))
            val range = start until start + piece.length
            start += piece.length
            range
        }.also {
            require(start == text.length)
        }
    }

    private fun icuSentenceRanges(text: String): List<IntRange> {
        val iterator = BreakIterator.getSentenceInstance(ULocale.UK)
        iterator.setText(text)
        val ranges = mutableListOf<IntRange>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val trimmed = text.substring(start, end).trimEnd()
            if (trimmed.any(Char::isLetterOrDigit)) {
                ranges += start until start + trimmed.length
            }
            start = end
            end = iterator.next()
        }
        return ranges
    }

    private data class MergeCase(
        val raw: List<String>,
        val expected: List<String>
    )

    private data class IcuCase(
        val text: String,
        val expected: List<String>
    )

    private class FixedRangeTokenizer(
        private val ranges: List<IntRange>
    ) : Tokenizer<String, IntRange> {
        override fun tokenize(data: String): List<IntRange> = ranges
    }
}
