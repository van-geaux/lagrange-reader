package com.bookorbit.android

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
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
    fun pdfium_adapter_opens_pdf_as_readium_publication_with_page_positions() = runBlocking {
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
