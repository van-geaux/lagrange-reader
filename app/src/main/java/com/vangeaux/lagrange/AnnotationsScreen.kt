package com.vangeaux.lagrange

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun AnnotationsScreen(
    loader: suspend (AnnotationsFilter, Int) -> BookAnnotationsPage,
    onAnnotationSelected: (BookAnnotation) -> Unit = {},
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
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable { onAnnotationSelected(annotation) }
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(annotation.bookTitle ?: "Unknown book", style = MaterialTheme.typography.titleMedium)
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
}
