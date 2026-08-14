package com.vangeaux.lagrange

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The actions offered from an annotation card's overflow menu / long-press menu. The set
 * offered depends only on whether the annotation is currently trashed, so it is easy to test
 * without any Compose UI test infrastructure.
 */
internal enum class AnnotationMenuAction {
    OPEN_BOOK_DETAILS,
    GO_TO_ANNOTATION,
    EDIT_NOTE,
    CHANGE_STYLE,
    COPY_TEXT,
    TRASH,
    RESTORE,
    PURGE
}

internal fun annotationMenuActions(annotation: BookAnnotation): List<AnnotationMenuAction> =
    if (annotation.deletedAt.isNullOrBlank()) {
        listOf(
            AnnotationMenuAction.OPEN_BOOK_DETAILS,
            AnnotationMenuAction.GO_TO_ANNOTATION,
            AnnotationMenuAction.EDIT_NOTE,
            AnnotationMenuAction.CHANGE_STYLE,
            AnnotationMenuAction.COPY_TEXT,
            AnnotationMenuAction.TRASH
        )
    } else {
        listOf(AnnotationMenuAction.RESTORE, AnnotationMenuAction.PURGE)
    }

internal fun annotationMenuActionLabel(action: AnnotationMenuAction): String = when (action) {
    AnnotationMenuAction.OPEN_BOOK_DETAILS -> "Open book details"
    AnnotationMenuAction.GO_TO_ANNOTATION -> "Go to annotation"
    AnnotationMenuAction.EDIT_NOTE -> "Edit note"
    AnnotationMenuAction.CHANGE_STYLE -> "Change color / style"
    AnnotationMenuAction.COPY_TEXT -> "Copy text"
    AnnotationMenuAction.TRASH -> "Move to trash"
    AnnotationMenuAction.RESTORE -> "Restore"
    AnnotationMenuAction.PURGE -> "Delete permanently"
}

/**
 * Tracks which single annotation card currently has its menu expanded. Both the three-dots
 * overflow button and a long-press on the card call into [openFromOverflow] / [openFromLongPress],
 * which both delegate to the same underlying state, so the two triggers always produce the
 * identical menu (same [annotationMenuActions]) for the identical annotation.
 */
internal class AnnotationMenuOpener {
    private val expanded = mutableStateOf<String?>(null)

    val expandedAnnotationId: String? get() = expanded.value

    fun openFromOverflow(annotationId: String) {
        expanded.value = annotationId
    }

    fun openFromLongPress(annotationId: String) {
        expanded.value = annotationId
    }

    fun dismiss() {
        expanded.value = null
    }

    fun isExpandedFor(annotationId: String): Boolean = expanded.value == annotationId
}

private const val EDIT_NOTE_DIALOG = "edit_note"
private const val CHANGE_STYLE_DIALOG = "change_style"
private const val CONFIRM_TRASH_DIALOG = "confirm_trash"
private const val CONFIRM_PURGE_DIALOG = "confirm_purge"

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AnnotationsScreen(
    loader: suspend (AnnotationsFilter, Int) -> BookAnnotationsPage,
    onAnnotationSelected: (BookAnnotation) -> Unit = {},
    onBookDetails: (BookSummary) -> Unit = {},
    onUpdateAnnotation: suspend (annotation: BookAnnotation, note: String?, color: String?, style: String?) -> Boolean =
        { _, _, _, _ -> false },
    onTrashAnnotation: suspend (BookAnnotation) -> Boolean = { false },
    onRestoreAnnotation: suspend (BookAnnotation) -> Boolean = { false },
    onPurgeAnnotation: suspend (BookAnnotation) -> Boolean = { false },
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("active") }
    var hasNote by remember { mutableStateOf<Boolean?>(null) }
    var sortDir by remember { mutableStateOf("desc") }
    var page by remember { mutableIntStateOf(1) }
    var result by remember { mutableStateOf<BookAnnotationsPage?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    val menuOpener = remember { AnnotationMenuOpener() }
    var dialogAnnotation by remember { mutableStateOf<BookAnnotation?>(null) }
    var activeDialog by remember { mutableStateOf<String?>(null) }
    var noteDraft by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(query, page, status, hasNote, sortDir) {
        loading = true
        error = false
        runCatching {
            loader(
                AnnotationsFilter(search = query, status = status, hasNote = hasNote, sortDir = sortDir),
                page
            )
        }
            .onSuccess { result = it }
            .onFailure { error = true }
        loading = false
    }

    fun removeAnnotationFromList(annotationId: String) {
        result = result?.let { current -> current.copy(items = current.items.filterNot { it.id == annotationId }) }
    }

    fun replaceAnnotationInList(updated: BookAnnotation) {
        result = result?.let { current ->
            current.copy(items = current.items.map { if (it.id == updated.id) updated else it })
        }
    }

    fun closeMenuAndDialog() {
        menuOpener.dismiss()
        activeDialog = null
        dialogAnnotation = null
    }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; page = 1 },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            singleLine = true,
            placeholder = { Text("Search highlights and notes") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = { status = if (status == "active") "trashed" else "active"; page = 1 }) {
                Text(if (status == "active") "Active" else "Trashed")
            }
            TextButton(onClick = { hasNote = if (hasNote == true) null else true; page = 1 }) {
                Text(if (hasNote == true) "With notes" else "All notes")
            }
            TextButton(onClick = { sortDir = if (sortDir == "desc") "asc" else "desc"; page = 1 }) {
                Text(if (sortDir == "desc") "Newest" else "Oldest")
            }
        }
        actionMessage?.let { message ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
        }
        when {
            loading && result == null -> Column(Modifier.padding(16.dp)) { CircularProgressIndicator() }
            error && result == null -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Annotations could not be loaded.")
                Button(onClick = { page = 1 }) { Text("Retry") }
            }
            result?.items.isNullOrEmpty() -> Text("No annotations found.", modifier = Modifier.padding(16.dp))
            else -> {
                val current = result ?: return@Column
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(current.items, key = { it.id }) { annotation ->
                        val actions = annotationMenuActions(annotation)
                        Box {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .combinedClickable(
                                        onClick = { onAnnotationSelected(annotation) },
                                        onLongClick = { menuOpener.openFromLongPress(annotation.id) }
                                    )
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            annotation.bookTitle ?: "Unknown book",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Box {
                                            IconButton(onClick = { menuOpener.openFromOverflow(annotation.id) }) {
                                                Icon(
                                                    imageVector = Icons.Default.MoreVert,
                                                    contentDescription = "More options for annotation in ${annotation.bookTitle ?: "Unknown book"}"
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = menuOpener.isExpandedFor(annotation.id),
                                                onDismissRequest = { menuOpener.dismiss() }
                                            ) {
                                                actions.forEach { action ->
                                                    DropdownMenuItem(
                                                        text = { Text(annotationMenuActionLabel(action)) },
                                                        onClick = {
                                                            menuOpener.dismiss()
                                                            when (action) {
                                                                AnnotationMenuAction.OPEN_BOOK_DETAILS -> {
                                                                    onBookDetails(annotation.toBookSummary())
                                                                }
                                                                AnnotationMenuAction.GO_TO_ANNOTATION -> {
                                                                    onAnnotationSelected(annotation)
                                                                }
                                                                AnnotationMenuAction.EDIT_NOTE -> {
                                                                    noteDraft = annotation.note.orEmpty()
                                                                    dialogAnnotation = annotation
                                                                    activeDialog = EDIT_NOTE_DIALOG
                                                                }
                                                                AnnotationMenuAction.CHANGE_STYLE -> {
                                                                    dialogAnnotation = annotation
                                                                    activeDialog = CHANGE_STYLE_DIALOG
                                                                }
                                                                AnnotationMenuAction.COPY_TEXT -> {
                                                                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                                                                    clipboard?.setPrimaryClip(
                                                                        ClipData.newPlainText("Annotation text", annotation.text.orEmpty())
                                                                    )
                                                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                                                }
                                                                AnnotationMenuAction.TRASH -> {
                                                                    dialogAnnotation = annotation
                                                                    activeDialog = CONFIRM_TRASH_DIALOG
                                                                }
                                                                AnnotationMenuAction.RESTORE -> {
                                                                    scope.launch {
                                                                        if (onRestoreAnnotation(annotation)) {
                                                                            removeAnnotationFromList(annotation.id)
                                                                            actionMessage = null
                                                                        } else {
                                                                            actionMessage = "Unable to restore this annotation."
                                                                        }
                                                                    }
                                                                }
                                                                AnnotationMenuAction.PURGE -> {
                                                                    dialogAnnotation = annotation
                                                                    activeDialog = CONFIRM_PURGE_DIALOG
                                                                }
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    annotation.author?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                    Text("“${annotation.text}”", style = MaterialTheme.typography.bodyLarge)
                                    annotation.note?.takeIf { it.isNotBlank() }?.let { Text("Note: $it") }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        annotation.chapterTitle?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                        annotation.pageno?.let { Text("Page ${it + 1}", style = MaterialTheme.typography.bodySmall) }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { page-- }, enabled = page > 1) { Text("Previous") }
                            Button(onClick = { page++ }, enabled = page * (current.pageSize ?: ANNOTATIONS_PAGE_SIZE) < (current.total ?: 0)) { Text("Next") }
                        }
                    }
                }
            }
        }
    }

    val dialogTarget = dialogAnnotation
    if (dialogTarget != null) {
        when (activeDialog) {
            EDIT_NOTE_DIALOG -> AlertDialog(
                onDismissRequest = { closeMenuAndDialog() },
                title = { Text("Edit note") },
                text = {
                    OutlinedTextField(
                        value = noteDraft,
                        onValueChange = { noteDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Add a note") }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            if (onUpdateAnnotation(dialogTarget, noteDraft, null, null)) {
                                replaceAnnotationInList(dialogTarget.copy(note = noteDraft))
                                actionMessage = null
                            } else {
                                actionMessage = "Unable to update the note."
                            }
                            closeMenuAndDialog()
                        }
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { closeMenuAndDialog() }) { Text("Cancel") }
                }
            )
            CHANGE_STYLE_DIALOG -> AlertDialog(
                onDismissRequest = { closeMenuAndDialog() },
                title = { Text("Highlight style") },
                text = {
                    Column {
                        epubHighlightChoices().forEach { choice ->
                            TextButton(onClick = {
                                scope.launch {
                                    if (onUpdateAnnotation(dialogTarget, null, choice.color, choice.style)) {
                                        replaceAnnotationInList(dialogTarget.copy(color = choice.color, style = choice.style))
                                        actionMessage = null
                                    } else {
                                        actionMessage = "Unable to update the highlight style."
                                    }
                                    closeMenuAndDialog()
                                }
                            }) { Text(choice.label) }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { closeMenuAndDialog() }) { Text("Cancel") }
                }
            )
            CONFIRM_TRASH_DIALOG -> AlertDialog(
                onDismissRequest = { closeMenuAndDialog() },
                title = { Text("Move to trash?") },
                text = { Text("This annotation will be moved to trash. You can restore it later.") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            if (onTrashAnnotation(dialogTarget)) {
                                removeAnnotationFromList(dialogTarget.id)
                                actionMessage = null
                            } else {
                                actionMessage = "Unable to move this annotation to trash."
                            }
                            closeMenuAndDialog()
                        }
                    }) { Text("Trash") }
                },
                dismissButton = {
                    TextButton(onClick = { closeMenuAndDialog() }) { Text("Cancel") }
                }
            )
            CONFIRM_PURGE_DIALOG -> AlertDialog(
                onDismissRequest = { closeMenuAndDialog() },
                title = { Text("Permanently delete?") },
                text = { Text("This annotation will be permanently deleted. This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            if (onPurgeAnnotation(dialogTarget)) {
                                removeAnnotationFromList(dialogTarget.id)
                                actionMessage = null
                            } else {
                                actionMessage = "Unable to permanently delete this annotation."
                            }
                            closeMenuAndDialog()
                        }
                    }) { Text("Delete permanently") }
                },
                dismissButton = {
                    TextButton(onClick = { closeMenuAndDialog() }) { Text("Cancel") }
                }
            )
        }
    }
}
