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

internal fun shouldUseReadiumEpubPreview(
    mediaKind: MediaKind,
    launchMode: ReaderLaunchMode
): Boolean = mediaKind == MediaKind.EPUB && launchMode == ReaderLaunchMode.PREVIEW

@Composable
internal fun ReadiumEpubPreviewLauncher(
    file: File,
    title: String,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val latestOnFinished by rememberUpdatedState(onFinished)
    var launched by remember(file) { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        latestOnFinished()
    }
    LaunchedEffect(file, title) {
        if (!launched) {
            launched = true
            launcher.launch(ReadiumEpubPreviewActivity.createIntent(context, file, title))
        }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
