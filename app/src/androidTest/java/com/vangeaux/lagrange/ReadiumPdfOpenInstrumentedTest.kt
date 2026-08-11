package com.vangeaux.lagrange

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions

@RunWith(AndroidJUnit4::class)
class ReadiumPdfOpenInstrumentedTest {
    @Test
    fun pdfium_adapter_opens_pdf_as_readium_publication_with_page_positions() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val file = File(context.cacheDir, "readium-${System.nanoTime()}.pdf")
            createPdf(file, pageCount = 3)

            val result = openReadiumPdf(context, file)

            assertTrue(result is ReadiumPdfOpenResult.Opened)
            val publication = (result as ReadiumPdfOpenResult.Opened).publication
            assertTrue(publication.conformsTo(Publication.Profile.PDF))
            assertEquals(3, publication.positions().size)
            publication.close()
            file.delete()
        }
    }

    @Test
    fun recreatesPdfReaderTwiceAtTheSameLocator() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "readium-recreate-${System.nanoTime()}.pdf")
        val readerKey = "instrumented-pdf-recreate-${System.nanoTime()}"
        createPdf(file, pageCount = 3)

        ActivityScenario.launch<ReadiumPdfReaderActivity>(
            ReadiumPdfReaderActivity.createIntent(
                context = context,
                file = file,
                title = "PDF recreation",
                readerKey = readerKey,
                launchMode = ReaderLaunchMode.NORMAL,
                initialPage = 0
            )
        ).use { scenario ->
            val expected = awaitPdfLocator(scenario, context, readerKey)
            repeat(2) {
                scenario.recreate()
                val restored = awaitPdfLocator(scenario, context, readerKey)
                assertEquals(expected.href, restored.href)
                assertEquals(expected.locations.position, restored.locations.position)
                scenario.onActivity { activity ->
                    assertEquals(
                        1,
                        activity.supportFragmentManager.fragments.count { fragment ->
                            fragment.tag == "readium_pdf_navigator"
                        }
                    )
                    assertEquals(false, activity.isFinishing)
                }
            }
        }
        file.delete()
    }

    private fun awaitPdfLocator(
        scenario: ActivityScenario<ReadiumPdfReaderActivity>,
        context: Context,
        readerKey: String
    ): org.readium.r2.shared.publication.Locator {
        repeat(40) {
            var ready = false
            scenario.onActivity { activity ->
                ready = activity.supportFragmentManager
                    .findFragmentByTag("readium_pdf_navigator") is
                    org.readium.r2.navigator.pdf.PdfNavigatorFragment<*, *>
            }
            val locator = ReadiumPdfLocatorStore(context).read(readerKey)
            if (ready && locator != null) return locator
            SystemClock.sleep(250)
        }
        error("PDF navigator and locator did not become ready")
    }

    private fun createPdf(file: File, pageCount: Int) {
        val document = PdfDocument()
        repeat(pageCount) { index ->
            val page = document.startPage(
                PdfDocument.PageInfo.Builder(300, 400, index + 1).create()
            )
            page.canvas.drawText("Page ${index + 1}", 40f, 80f, Paint().apply { textSize = 24f })
            document.finishPage(page)
        }
        file.outputStream().use(document::writeTo)
        document.close()
    }
}
