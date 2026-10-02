package com.vangeaux.lagrange

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.readium.navigator.media.tts.TtsEngine
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.navigator.media.tts.android.AndroidTtsPreferences
import org.readium.navigator.media.tts.android.AndroidTtsSettings
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.util.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PunctuationPausingTtsEngineTest {
    @Test
    fun `chunks add configured pauses without changing source text or offsets`() {
        val text = "He said (quietly): wait\u2014then go."

        val chunks = epubTtsSpeechChunks(
            text,
            EpubTtsPauseSettings(
                colonMillis = 250,
                emDashMillis = 350,
                parenthesesMillis = 200
            )
        )

        assertEquals(text, chunks.joinToString("") { it.text })
        assertEquals(listOf(200, 450, 350, 0), chunks.map { it.pauseAfterMillis })
        chunks.forEach { chunk ->
            assertEquals(chunk.text, text.substring(chunk.sourceStart, chunk.sourceStart + chunk.text.length))
        }
    }

    @Test
    fun `zero pauses do not split and prose filtering avoids time and URL colons`() {
        val text = "At 12:30 see https://example.test: then leave."

        val chunks = epubTtsSpeechChunks(
            text,
            EpubTtsPauseSettings(colonMillis = 300, emDashMillis = 0, parenthesesMillis = 0)
        )

        assertEquals(text, chunks.joinToString("") { it.text })
        assertEquals(listOf(300, 0), chunks.map { it.pauseAfterMillis })
        assertEquals("At 12:30 see https://example.test:", chunks.first().text)
    }

    @Test
    fun `leading opening parenthesis is not sent as punctuation-only speech`() {
        val chunks = epubTtsSpeechChunks(
            "(aside) Continue.",
            EpubTtsPauseSettings(colonMillis = 0, emDashMillis = 0, parenthesesMillis = 175)
        )

        assertEquals(listOf("(aside)", " Continue."), chunks.map { it.text })
        assertEquals(listOf(175, 0), chunks.map { it.pauseAfterMillis })
    }

    @Test
    fun `comma semicolon ellipses and all grouping marks use their categories`() {
        val text = "Wait, item[one]; brace{two}... Then\u2026 done, with 1,000."

        val chunks = epubTtsSpeechChunks(
            text,
            EpubTtsPauseSettings(
                commaMillis = 100,
                semicolonMillis = 200,
                colonMillis = 0,
                emDashMillis = 0,
                ellipsisMillis = 300,
                parenthesesMillis = 50
            )
        )

        assertEquals(text, chunks.joinToString("") { it.text })
        assertEquals(
            listOf(100, 50, 250, 50, 350, 300, 100, 0),
            chunks.map { it.pauseAfterMillis }
        )
        assertEquals(" with 1,000.", chunks.last().text)
    }

    @Test
    fun `custom comma pause requires following whitespace`() {
        val text = "alpha,beta, gamma"

        val chunks = epubTtsSpeechChunks(
            text,
            EpubTtsPauseSettings(
                commaMillis = 150,
                semicolonMillis = 0,
                colonMillis = 0,
                emDashMillis = 0,
                ellipsisMillis = 0,
                parenthesesMillis = 0
            )
        )

        assertEquals(listOf("alpha,beta,", " gamma"), chunks.map { it.text })
        assertEquals(listOf(150, 0), chunks.map { it.pauseAfterMillis })
    }

    @Test
    fun `pause values scale inversely with playback speed and can be disabled`() {
        assertEquals(125, scaleEpubTtsPauseMillis(250, 2f))
        assertEquals(250, scaleEpubTtsPauseMillis(250, 1f))
        assertEquals(500, scaleEpubTtsPauseMillis(250, 0.5f))

        val text = "Wait, then; continue…"
        val chunks = epubTtsSpeechChunks(
            text,
            EpubTtsPauseSettings(enabled = false),
            playbackSpeed = 0.5f
        )
        assertEquals(listOf(EpubTtsSpeechChunk(text, 0, 0)), chunks)
    }

    @Test
    fun `hostile punctuation cannot create unbounded chunks or silence`() {
        val text = (1..1_000).joinToString(separator = "") { "word:" }

        val chunks = epubTtsSpeechChunks(
            text,
            EpubTtsPauseSettings(colonMillis = 2_000, emDashMillis = 2_000, parenthesesMillis = 2_000)
        )

        assertEquals(text, chunks.joinToString("") { it.text })
        assertTrue(chunks.size <= EPUB_TTS_MAX_CHUNKS_PER_UTTERANCE)
        assertTrue(
            chunks.sumOf { it.pauseAfterMillis } <=
                EPUB_TTS_MAX_TOTAL_PAUSE_MILLIS_PER_UTTERANCE
        )
    }

    @Test
    fun `consecutive punctuation cannot bypass the split work limit`() {
        val text = "word" + ":".repeat(20_000)

        val chunks = epubTtsSpeechChunks(
            text,
            EpubTtsPauseSettings(
                commaMillis = 1,
                semicolonMillis = 1,
                colonMillis = 1,
                emDashMillis = 1,
                ellipsisMillis = 1,
                parenthesesMillis = 1
            )
        )

        assertEquals(text, chunks.joinToString("") { it.text })
        assertTrue(chunks.size <= EPUB_TTS_MAX_CHUNKS_PER_UTTERANCE)
        assertEquals(EPUB_TTS_MAX_CHUNKS_PER_UTTERANCE - 1, chunks.sumOf { it.pauseAfterMillis })
        assertEquals("word".length + EPUB_TTS_MAX_CHUNKS_PER_UTTERANCE - 1, chunks.first().text.length)
    }

    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalReadiumApi::class)
    @Test
    fun `engine waits between chunks and remaps ranges to original sentence`() = runTest {
        val delegate = FakeAndroidTtsEngine()
        val engine = PunctuationPausingTtsEngine(
            delegate = delegate,
            pauseSettings = {
                EpubTtsPauseSettings(colonMillis = 300, emDashMillis = 0, parenthesesMillis = 0)
            },
            scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        )
        val listener = RecordingListener()
        engine.setListener(listener)
        val externalId = TtsEngine.RequestId("book-sentence")

        engine.speak(externalId, "First: second", Language("en"))
        assertEquals(listOf("First:"), delegate.spokenTexts)
        delegate.emitStart()
        delegate.emitRange(0..4)
        delegate.emitDone()
        runCurrent()
        assertEquals(listOf("First:"), delegate.spokenTexts)

        advanceTimeBy(299)
        runCurrent()
        assertEquals(listOf("First:"), delegate.spokenTexts)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("First:", " second"), delegate.spokenTexts)

        delegate.emitRange(1..6)
        delegate.emitDone()
        runCurrent()
        assertEquals(listOf(externalId), listener.started)
        assertEquals(listOf(0..4, 7..12), listener.ranges)
        assertEquals(listOf(externalId), listener.done)
    }

    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalReadiumApi::class)
    @Test
    fun `stopping during an added pause cannot start the next chunk`() = runTest {
        val delegate = FakeAndroidTtsEngine()
        val engine = PunctuationPausingTtsEngine(
            delegate = delegate,
            pauseSettings = {
                EpubTtsPauseSettings(colonMillis = 500, emDashMillis = 0, parenthesesMillis = 0)
            },
            scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        )
        val listener = RecordingListener()
        engine.setListener(listener)
        val externalId = TtsEngine.RequestId("cancel-me")

        engine.speak(externalId, "First: second", Language("en"))
        delegate.emitStart()
        delegate.emitDone()
        runCurrent()
        engine.stop()
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(listOf("First:"), delegate.spokenTexts)
        assertEquals(1, delegate.stopCount)
        assertEquals(listOf(externalId), listener.interrupted)
        assertEquals(emptyList<TtsEngine.RequestId>(), listener.done)
    }

    @OptIn(ExperimentalReadiumApi::class)
    private class FakeAndroidTtsEngine : TtsEngine<AndroidTtsSettings, AndroidTtsPreferences,
        AndroidTtsEngine.Error, AndroidTtsEngine.Voice> {
        private data class Spoken(
            val id: TtsEngine.RequestId,
            val text: String
        )

        private val spoken = mutableListOf<Spoken>()
        private var listener: TtsEngine.Listener<AndroidTtsEngine.Error>? = null
        private val mutableSettings = MutableStateFlow(
            AndroidTtsSettings(
                language = Language("en"),
                overrideContentLanguage = false,
                pitch = 1.0,
                speed = 1.0,
                voices = emptyMap()
            )
        )

        val spokenTexts: List<String>
            get() = spoken.map { it.text }
        var stopCount: Int = 0
            private set

        override val voices: Set<AndroidTtsEngine.Voice> = emptySet()
        override val settings: StateFlow<AndroidTtsSettings> = mutableSettings

        override fun submitPreferences(preferences: AndroidTtsPreferences) = Unit

        override fun speak(
            requestId: TtsEngine.RequestId,
            text: String,
            language: Language?
        ) {
            spoken += Spoken(requestId, text)
        }

        override fun stop() {
            stopCount += 1
        }

        override fun setListener(listener: TtsEngine.Listener<AndroidTtsEngine.Error>?) {
            this.listener = listener
        }

        override fun close() = Unit

        fun emitStart() = listener?.onStart(spoken.last().id)
        fun emitRange(range: IntRange) = listener?.onRange(spoken.last().id, range)
        fun emitDone() = listener?.onDone(spoken.last().id)
    }

    @OptIn(ExperimentalReadiumApi::class)
    private class RecordingListener : TtsEngine.Listener<AndroidTtsEngine.Error> {
        val started = mutableListOf<TtsEngine.RequestId>()
        val ranges = mutableListOf<IntRange>()
        val interrupted = mutableListOf<TtsEngine.RequestId>()
        val done = mutableListOf<TtsEngine.RequestId>()

        override fun onStart(requestId: TtsEngine.RequestId) {
            started += requestId
        }

        override fun onRange(requestId: TtsEngine.RequestId, range: IntRange) {
            ranges += range
        }

        override fun onInterrupted(requestId: TtsEngine.RequestId) {
            interrupted += requestId
        }

        override fun onFlushed(requestId: TtsEngine.RequestId) = Unit

        override fun onDone(requestId: TtsEngine.RequestId) {
            done += requestId
        }

        override fun onError(requestId: TtsEngine.RequestId, error: AndroidTtsEngine.Error) = Unit
    }
}
