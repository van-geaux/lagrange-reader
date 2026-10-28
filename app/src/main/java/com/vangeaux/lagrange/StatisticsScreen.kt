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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun StatisticsScreen(
    loader: suspend () -> UserStatistics,
    modifier: Modifier = Modifier
) {
    var reloadKey by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    var lastSuccessful by remember { mutableStateOf<UserStatistics?>(null) }
    val statistics by produceState<UserStatistics?>(initialValue = lastSuccessful, reloadKey) {
        val previous = value
        try {
            val result = loader()
            if (result.status == UserStatisticsStatus.AVAILABLE) lastSuccessful = result
            value = if (result.status != UserStatisticsStatus.AVAILABLE &&
                previous?.status == UserStatisticsStatus.AVAILABLE
            ) {
                previous
            } else {
                result
            }
        } finally {
            isRefreshing = false
        }
    }
    val onRefresh: () -> Unit = {
        if (!isRefreshing) {
            isRefreshing = true
            reloadKey += 1
        }
    }

    PullToRefreshLayout(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
            .fillMaxSize()
            .testTag("statistics_pull_to_refresh")
    ) {
        when (val loaded = statistics) {
            null -> StatisticsCenteredState(Modifier.fillMaxSize()) {
                CircularProgressIndicator()
                Text(stringResource(R.string.secondary_statistics_loading))
            }
            else -> when (loaded.status) {
                UserStatisticsStatus.UNSUPPORTED -> StatisticsCenteredState(Modifier.fillMaxSize()) {
                    Icon(Icons.Default.Insights, contentDescription = null)
                    Text(stringResource(R.string.secondary_statistics_unavailable))
                }
                UserStatisticsStatus.ERROR -> StatisticsCenteredState(Modifier.fillMaxSize()) {
                    Text(
                        stringResource(R.string.secondary_statistics_error),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Button(onClick = onRefresh) { Text(stringResource(R.string.secondary_try_again)) }
                }
                UserStatisticsStatus.AVAILABLE -> StatisticsContent(loaded, Modifier.fillMaxSize())
            }
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
            Text(stringResource(R.string.secondary_statistics_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.secondary_statistics_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (summary == null) {
            item { Text(stringResource(R.string.secondary_statistics_no_summary)) }
        } else {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            stringResource(R.string.secondary_statistics_library_progress),
                            style = MaterialTheme.typography.titleMedium
                        )
                        StatisticRow(stringResource(R.string.secondary_statistics_tracked_books), summary.trackedBooks.toString())
                        StatisticRow(stringResource(R.string.secondary_statistics_started), summary.startedBooks.toString())
                        StatisticRow(stringResource(R.string.secondary_statistics_in_progress), summary.inProgressBooks.toString())
                        StatisticRow(stringResource(R.string.secondary_statistics_completed), summary.completedBooks.toString())
                        Text(
                            stringResource(
                                R.string.secondary_statistics_mean_progress,
                                formatStatisticPercent(summary.meanProgressPercent)
                            ),
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
            Text(stringResource(R.string.secondary_statistics_daily_reading), style = MaterialTheme.typography.titleMedium)
        }
        if (statistics.dailyReading.isEmpty()) {
            item { Text(stringResource(R.string.secondary_statistics_no_daily_reading)) }
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
                                stringResource(R.string.secondary_statistics_reading_events, day.eventsCount),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(statisticDurationLabel(day.readingSeconds))
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

@Composable
private fun statisticDurationLabel(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    return if (hours > 0) {
        stringResource(R.string.secondary_statistics_duration_hours, hours, minutes)
    } else {
        stringResource(R.string.secondary_statistics_duration_minutes, minutes)
    }
}

private fun formatStatisticPercent(value: Double): String =
    "${String.format(Locale.US, "%.1f", value.coerceIn(0.0, 100.0))}%"
