package com.bookorbit.android

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
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

internal fun shouldUseReadiumEpubReader(mediaKind: MediaKind): Boolean =
    mediaKind == MediaKind.EPUB

@Composable
internal fun ReadiumEpubReaderLauncher(
    file: File,
    title: String,
    readerKey: String,
    launchMode: ReaderLaunchMode,
    initialChapter: Int,
    initialPage: Int,
    initialPageCount: Int,
    initialPercent: Float?,
    onProgress: (chapterIndex: Int, pageIndex: Int, pageCount: Int, percent: Float?) -> Unit,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val latestOnFinished by rememberUpdatedState(onFinished)
    val latestOnProgress by rememberUpdatedState(onProgress)
    var launched by remember(file, launchMode) { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        ReadiumEpubReaderActivity.readProgressResult(result.data)?.let { progress ->
            latestOnProgress(
                progress.chapterIndex,
                progress.pageIndex,
                progress.pageCount,
                progress.percent
            )
        }
        latestOnFinished()
    }
    LaunchedEffect(file, title, readerKey, launchMode) {
        if (!launched) {
            launched = true
            launcher.launch(
                ReadiumEpubReaderActivity.createIntent(
                    context = context,
                    file = file,
                    title = title,
                    readerKey = readerKey,
                    launchMode = launchMode,
                    initialChapter = initialChapter,
                    initialPage = initialPage,
                    initialPageCount = initialPageCount,
                    initialPercent = initialPercent
                )
            )
        }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
