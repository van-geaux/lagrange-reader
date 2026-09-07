package com.vangeaux.lagrange

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun EpubImageLibraryViewer(
    title: String,
    catalog: EpubImageCatalog,
    onDismiss: () -> Unit
) {
    if (catalog.entries.isEmpty()) return
    var selectedIndexState by rememberSaveable(catalog.sourceFile.absolutePath) {
        mutableIntStateOf(0)
    }
    val selectedIndex = selectedIndexState.coerceIn(catalog.entries.indices)
    val selectedEntry = catalog.entries[selectedIndex]
    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        catalog.sourceFile.absolutePath,
        selectedEntry.archivePath
    ) {
        value = withContext(Dispatchers.IO) {
            EpubImageLibraryScanner.decodeEpubImageBitmap(
                catalog.sourceFile,
                selectedEntry.archivePath,
                4096
            )
        }
    }

    if (bitmap == null) {
        EpubImageLibraryLoadingDialog(onDismiss)
    } else {
        ComicPageImageViewer(
            title = title,
            pageIndex = selectedIndex,
            bitmap = bitmap!!,
            onDismiss = onDismiss,
            onSwipePrevious = {
                if (selectedIndex > 0) selectedIndexState = selectedIndex - 1
            },
            onSwipeNext = {
                if (selectedIndex < catalog.entries.lastIndex) selectedIndexState = selectedIndex + 1
            },
            bottomContent = {
                EpubImageThumbnailStrip(
                    catalog = catalog,
                    selectedIndex = selectedIndex,
                    onSelected = { selectedIndexState = it }
                )
            },
            exportTitle = "$title - Image ${selectedIndex + 1}",
            exportBytes = {
                EpubImageLibraryScanner.readEpubImageBytes(catalog.sourceFile, selectedEntry.archivePath)
            }
        )
    }
}

@Composable
private fun EpubImageLibraryLoadingDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .padding(24.dp)
                    .clickable(enabled = false, onClick = {}),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Text("Finding EPUB images…")
                }
            }
        }
    }
}

@Composable
private fun EpubImageThumbnailStrip(
    catalog: EpubImageCatalog,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Black.copy(alpha = 0.72f)
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                items = catalog.entries,
                key = { _, entry -> entry.archivePath }
            ) { index, entry ->
                EpubImageThumbnail(
                    sourceFile = catalog.sourceFile,
                    entry = entry,
                    index = index,
                    selected = index == selectedIndex,
                    onClick = { onSelected(index) }
                )
            }
        }
    }
}

@Composable
private fun EpubImageThumbnail(
    sourceFile: java.io.File,
    entry: EpubImageEntry,
    index: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        sourceFile.absolutePath,
        entry.archivePath
    ) {
        value = withContext(Dispatchers.IO) {
            EpubImageLibraryScanner.decodeEpubImageBitmap(sourceFile, entry.archivePath, 160)
        }
    }
    Box(
        modifier = Modifier
            .size(68.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .padding(2.dp)
            .clickable(onClick = onClick)
            .semantics {
                this.selected = selected
                role = Role.Tab
                contentDescription = "Image ${index + 1} of EPUB gallery"
            },
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } ?: CircularProgressIndicator(modifier = Modifier.size(20.dp))
    }
}
