package com.bookorbit.android

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.util.mediatype.MediaType

internal fun shouldUseReadiumComicReader(
    book: BookSummary,
    localFile: File?,
    pagesUrl: String?
): Boolean {
    if (book.mediaKind != MediaKind.COMIC) return false
    if (localFile?.let(ReaderFileValidator::canRenderComicLocally) == true) return true
    return !pagesUrl.isNullOrBlank()
}

internal sealed interface ReadiumComicPreparationResult {
    data class Local(val file: File) : ReadiumComicPreparationResult

    data class Remote(
        val pagesUrl: String,
        val pageCount: Int,
        val pageMediaType: MediaType
    ) : ReadiumComicPreparationResult

    data class Error(val message: String) : ReadiumComicPreparationResult
}

internal suspend fun prepareReadiumComic(
    book: BookSummary,
    localFile: File?,
    pagesUrl: String?,
    pageLoader: suspend (String) -> ByteArray?
): ReadiumComicPreparationResult = withContext(Dispatchers.IO) {
    localFile
        ?.takeIf(File::exists)
        ?.takeIf(ReaderFileValidator::canRenderComicLocally)
        ?.let { return@withContext ReadiumComicPreparationResult.Local(it) }

    val archiveExtension = book.comicArchiveExtension()
    if (archiveExtension !in setOf("cbz", "cbr", "cb7") || pagesUrl.isNullOrBlank()) {
        return@withContext ReadiumComicPreparationResult.Error(
            "This comic could not be prepared as a Readium image publication."
        )
    }
    val pageCount = runCatching { pageLoader(pagesUrl) }
        .getOrNull()
        ?.let(::parseComicPageCount)
        ?.takeIf { it > 0 }
        ?: return@withContext ReadiumComicPreparationResult.Error(
            "BookOrbit could not provide this " +
                archiveExtension.orEmpty().uppercase(Locale.US) +
                " file's page list."
        )
    val firstPage = runCatching { pageLoader(pagesUrl.trimEnd('/') + "/0") }
        .getOrNull()
        ?: return@withContext ReadiumComicPreparationResult.Error(
            "BookOrbit could not provide the first comic page."
        )
    val mediaType = comicImageMediaType(firstPage)
        ?: return@withContext ReadiumComicPreparationResult.Error(
            "BookOrbit returned an unsupported comic-page image."
        )
    ReadiumComicPreparationResult.Remote(
        pagesUrl = pagesUrl.trimEnd('/'),
        pageCount = pageCount,
        pageMediaType = mediaType
    )
}

internal fun comicImageMediaType(bytes: ByteArray): MediaType? = when (comicImageExtension(bytes)) {
    "jpg" -> MediaType.JPEG
    "png" -> MediaType.PNG
    "gif" -> MediaType.GIF
    "webp" -> MediaType.WEBP
    "bmp" -> MediaType.BMP
    "tiff" -> MediaType.TIFF
    else -> null
}

internal fun comicImageExtension(bytes: ByteArray): String? = when {
    bytes.size >= 3 &&
        bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "jpg"
    bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    ) -> "png"
    bytes.size >= 6 && String(bytes, 0, 6, Charsets.US_ASCII).let {
        it == "GIF87a" || it == "GIF89a"
    } -> "gif"
    bytes.size >= 12 &&
        String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
        String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "webp"
    bytes.size >= 2 && bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte() -> "bmp"
    bytes.size >= 4 && (
        bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(0x49, 0x49, 0x2A, 0x00)) ||
            bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(0x4D, 0x4D, 0x00, 0x2A))
        ) -> "tiff"
    else -> null
}

@Composable
internal fun ReadiumComicReaderLauncher(
    book: BookSummary,
    file: File?,
    pagesUrl: String?,
    pageLoader: suspend (String) -> ByteArray?,
    title: String,
    readerKey: String,
    launchMode: ReaderLaunchMode,
    initialPage: Int,
    onProgress: (pageIndex: Int, pageCount: Int, percent: Float?) -> Unit,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val latestOnFinished by rememberUpdatedState(onFinished)
    val latestOnProgress by rememberUpdatedState(onProgress)
    var launched by remember(file, pagesUrl, launchMode) { mutableStateOf(false) }
    var preparation by remember(file, pagesUrl) {
        mutableStateOf<ReadiumComicPreparationResult?>(null)
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        ReadiumComicReaderActivity.readProgressResult(result.data)?.let { progress ->
            latestOnProgress(progress.pageIndex, progress.pageCount, progress.percent)
        }
        latestOnFinished()
    }
    LaunchedEffect(book, file, pagesUrl) {
        preparation = prepareReadiumComic(
            book = book,
            localFile = file,
            pagesUrl = pagesUrl,
            pageLoader = pageLoader
        )
    }
    LaunchedEffect(preparation, title, readerKey, launchMode) {
        if (launched) return@LaunchedEffect
        val intent = when (val ready = preparation) {
            is ReadiumComicPreparationResult.Local ->
                ReadiumComicReaderActivity.createIntent(
                    context = context,
                    file = ready.file,
                    title = title,
                    readerKey = readerKey,
                    launchMode = launchMode,
                    initialPage = initialPage
                )
            is ReadiumComicPreparationResult.Remote ->
                ReadiumComicReaderActivity.createRemoteIntent(
                    context = context,
                    pagesUrl = ready.pagesUrl,
                    pageCount = ready.pageCount,
                    pageMediaType = ready.pageMediaType,
                    title = title,
                    readerKey = readerKey,
                    launchMode = launchMode,
                    initialPage = initialPage
                )
            else -> null
        } ?: return@LaunchedEffect
        launched = true
        launcher.launch(intent)
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val result = preparation) {
            is ReadiumComicPreparationResult.Error -> Text(result.message)
            else -> CircularProgressIndicator()
        }
    }
}
