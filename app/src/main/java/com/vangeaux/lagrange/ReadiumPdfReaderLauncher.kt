package com.vangeaux.lagrange

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.io.File

internal fun shouldUseReadiumPdfReader(mediaKind: MediaKind, file: File?): Boolean =
    mediaKind == MediaKind.PDF && file?.let { ReaderFileValidator.isReadable(MediaKind.PDF, it) } == true

@Composable
internal fun ReadiumPdfReaderLauncher(
    file: File,
    fileId: String? = null,
    title: String,
    readerKey: String,
    libraryId: String,
    launchMode: ReaderLaunchMode,
    initialPage: Int,
    onProgress: (pageIndex: Int, pageCount: Int, percent: Float?) -> Unit,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val latestOnFinished by rememberUpdatedState(onFinished)
    val latestOnProgress by rememberUpdatedState(onProgress)
    var launchState by rememberSaveable(stateSaver = ReaderLaunchStateSaver) {
        mutableStateOf(ReaderLaunchState())
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        ReadiumPdfReaderActivity.readProgressResult(result.data)?.let { progress ->
            latestOnProgress(progress.pageIndex, progress.pageCount, progress.percent)
        }
        val reason = readerCompletionReason(
            result.data?.getStringExtra(EXTRA_READER_COMPLETION_REASON)
        )
        if (shouldCloseReader(reason)) latestOnFinished()
    }
    LaunchedEffect(file, title, readerKey, libraryId, launchMode, initialPage) {
        val token = listOf(file.absolutePath, readerKey, launchMode.name).joinToString("|")
        val claim = claimReaderLaunch(launchState, token)
        if (claim.shouldLaunch) {
            launchState = claim.state
            launcher.launch(
                ReadiumPdfReaderActivity.createIntent(
                    context = context,
                    file = file,
                    fileId = fileId,
                    title = title,
                    readerKey = readerKey,
                    libraryId = libraryId,
                    launchMode = launchMode,
                    initialPage = initialPage
                )
            )
        }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
