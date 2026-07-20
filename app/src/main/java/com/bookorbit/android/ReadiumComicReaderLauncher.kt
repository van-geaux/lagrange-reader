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
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun shouldUseReadiumComicReader(
    book: BookSummary,
    localFile: File?,
    pagesUrl: String?
): Boolean {
    if (book.mediaKind != MediaKind.COMIC) return false
    if (localFile?.let(ReaderFileValidator::canRenderComicLocally) == true) return true
    return book.comicArchiveExtension() in setOf("cbr", "cb7") && !pagesUrl.isNullOrBlank()
}

internal sealed interface ReadiumComicPreparationResult {
    data class Ready(val file: File) : ReadiumComicPreparationResult
    data class Error(val message: String) : ReadiumComicPreparationResult
}

internal suspend fun prepareReadiumComic(
    context: Context,
    book: BookSummary,
    localFile: File?,
    pagesUrl: String?,
    pageLoader: suspend (String) -> ByteArray?
): ReadiumComicPreparationResult = withContext(Dispatchers.IO) {
    localFile
        ?.takeIf(File::exists)
        ?.takeIf(ReaderFileValidator::canRenderComicLocally)
        ?.let { return@withContext ReadiumComicPreparationResult.Ready(it) }

    val archiveExtension = book.comicArchiveExtension()
    if (archiveExtension !in setOf("cbr", "cb7") || pagesUrl.isNullOrBlank()) {
        return@withContext ReadiumComicPreparationResult.Error(
            "This comic could not be prepared as a Readium image publication."
        )
    }
    val pageCount = runCatching { pageLoader(pagesUrl) }
        .getOrNull()
        ?.let(::parseComicPageCount)
        ?: return@withContext ReadiumComicPreparationResult.Error(
            "BookOrbit could not provide this ${archiveExtension.orEmpty().uppercase(Locale.US)} file's page list."
        )
    val cacheDir = File(context.cacheDir, "readium-comics").apply { mkdirs() }
    val cacheKey = sha256Hex(
        listOf(
            book.id,
            book.fileId.orEmpty(),
            pagesUrl,
            pageCount.toString(),
            book.updatedAtMillis?.toString().orEmpty()
        ).joinToString("|")
    )
    val target = File(cacheDir, "$cacheKey.cbz")
    if (ReaderFileValidator.canRenderComicLocally(target)) {
        return@withContext ReadiumComicPreparationResult.Ready(target)
    }
    val staged = File(cacheDir, "$cacheKey.part")
    runCatching {
        if (staged.exists()) staged.delete()
        ZipOutputStream(staged.outputStream().buffered()).use { zip ->
            repeat(pageCount) { pageIndex ->
                val bytes = pageLoader("$pagesUrl/$pageIndex")
                    ?: error("Page ${pageIndex + 1} could not be downloaded.")
                val extension = comicImageExtension(bytes)
                    ?: error("Page ${pageIndex + 1} is not a supported image.")
                zip.putNextEntry(
                    ZipEntry("${pageIndex.toString().padStart(8, '0')}.$extension")
                )
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        if (target.exists() && !target.delete()) error("The previous comic cache could not be replaced.")
        if (!staged.renameTo(target)) {
            staged.copyTo(target, overwrite = true)
            staged.delete()
        }
        check(ReaderFileValidator.canRenderComicLocally(target))
        target
    }.fold(
        onSuccess = { ReadiumComicPreparationResult.Ready(it) },
        onFailure = { error ->
            staged.delete()
            target.takeIf { !ReaderFileValidator.canRenderComicLocally(it) }?.delete()
            ReadiumComicPreparationResult.Error(
                error.message ?: "The comic pages could not be prepared for Readium."
            )
        }
    )
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

private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray())
    .joinToString("") { byte -> "%02x".format(Locale.US, byte.toInt() and 0xFF) }

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
            context = context,
            book = book,
            localFile = file,
            pagesUrl = pagesUrl,
            pageLoader = pageLoader
        )
    }
    LaunchedEffect(preparation, title, readerKey, launchMode) {
        val ready = preparation as? ReadiumComicPreparationResult.Ready ?: return@LaunchedEffect
        if (!launched) {
            launched = true
            launcher.launch(
                ReadiumComicReaderActivity.createIntent(
                    context = context,
                    file = ready.file,
                    title = title,
                    readerKey = readerKey,
                    launchMode = launchMode,
                    initialPage = initialPage
                )
            )
        }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val result = preparation) {
            is ReadiumComicPreparationResult.Error -> Text(result.message)
            else -> CircularProgressIndicator()
        }
    }
}
