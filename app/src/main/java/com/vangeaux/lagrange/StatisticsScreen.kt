package com.vangeaux.lagrange

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
internal fun StatisticsScreen(
    loader: suspend () -> UserStatistics,
    modifier: Modifier = Modifier
) {
    var reloadKey by remember { mutableIntStateOf(0) }
    val statistics by produceState<UserStatistics?>(initialValue = null, reloadKey) {
        value = loader()
    }

    when (val loaded = statistics) {
        null -> StatisticsCenteredState(modifier) {
            CircularProgressIndicator()
            Text("Loading statistics…")
        }
        else -> when (loaded.status) {
            UserStatisticsStatus.UNSUPPORTED -> StatisticsCenteredState(modifier) {
                Icon(Icons.Default.Insights, contentDescription = null)
                Text("Statistics aren't available on this server version.")
            }
            UserStatisticsStatus.ERROR -> StatisticsCenteredState(modifier) {
                Text("Statistics couldn't be loaded.", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { reloadKey += 1 }) { Text("Try again") }
            }
            UserStatisticsStatus.AVAILABLE -> StatisticsContent(loaded, modifier)
        }
    }
}

@Composable
private fun StatisticsContent(statistics: UserStatistics, modifier: Modifier) {
    val summary = statistics.summary
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Your statistics", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Reading activity reported by BookOrbit.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (summary == null) {
            item { Text("No summary data is available yet.") }
        } else {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Library progress", style = MaterialTheme.typography.titleMedium)
                        StatisticRow("Tracked books", summary.trackedBooks.toString())
                        StatisticRow("Started", summary.startedBooks.toString())
                        StatisticRow("In progress", summary.inProgressBooks.toString())
                        StatisticRow("Completed", summary.completedBooks.toString())
                        Text(
                            "Mean progress · ${formatStatisticPercent(summary.meanProgressPercent)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        LinearProgressIndicator(
                            progress = { (summary.meanProgressPercent / 100.0).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        item {
            Text("Daily reading", style = MaterialTheme.typography.titleMedium)
        }
        if (statistics.dailyReading.isEmpty()) {
            item { Text("No daily reading activity is available yet.") }
        } else {
            items(statistics.dailyReading.takeLast(30), key = { it.day }) { day ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(day.day, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${day.eventsCount} reading events",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(formatStatisticDuration(day.readingSeconds))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}

@Composable
private fun StatisticsCenteredState(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        content = content
    )
}

private fun formatStatisticDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun formatStatisticPercent(value: Double): String =
    "${String.format(Locale.US, "%.1f", value.coerceIn(0.0, 100.0))}%"
