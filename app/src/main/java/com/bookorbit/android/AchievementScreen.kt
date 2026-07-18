package com.bookorbit.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
internal fun AchievementsScreen(
    loader: suspend () -> AchievementCatalogue,
    modifier: Modifier = Modifier
) {
    var reloadKey by remember { mutableIntStateOf(0) }
    val catalogue by produceState<AchievementCatalogue?>(initialValue = null, reloadKey) {
        value = loader()
    }

    when (val loaded = catalogue) {
        null -> AchievementCenteredState(modifier = modifier) {
            CircularProgressIndicator()
            Text("Loading achievements…")
        }
        else -> when (loaded.status) {
            AchievementCatalogueStatus.UNSUPPORTED -> AchievementCenteredState(modifier = modifier) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(48.dp))
                Text("Achievements aren't available on this server version.")
            }
            AchievementCatalogueStatus.ERROR -> AchievementCenteredState(modifier = modifier) {
                Text("Achievements couldn't be loaded.", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { reloadKey += 1 }) { Text("Try again") }
            }
            AchievementCatalogueStatus.AVAILABLE -> AchievementCatalogueGrid(
                catalogue = loaded,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun AchievementCenteredState(
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
private fun AchievementCatalogueGrid(
    catalogue: AchievementCatalogue,
    modifier: Modifier
) {
    val unlocked = catalogue.items.filter { it.earned }
    val locked = catalogue.items.filterNot { it.earned }
    val overallProgress = if (catalogue.totalAvailable > 0) {
        catalogue.totalEarned.toFloat() / catalogue.totalAvailable
    } else {
        0f
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 168.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Your achievements", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "${catalogue.totalEarned} of ${catalogue.totalAvailable} unlocked",
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                            )
                        }
                        Icon(
                            Icons.Default.EmojiEvents,
                            contentDescription = null,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    LinearProgressIndicator(
                        progress = { overallProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        achievementSection(
            title = "Unlocked",
            emptyMessage = "No achievements unlocked yet.",
            items = unlocked
        )
        achievementSection(
            title = "Locked",
            emptyMessage = "Everything is unlocked.",
            items = locked
        )
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.achievementSection(
    title: String,
    emptyMessage: String,
    items: List<AchievementItem>
) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(
            title,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
    if (items.isEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        items(items = items, key = { it.key }) { achievement ->
            AchievementCard(achievement)
        }
    }
}

@Composable
private fun AchievementCard(achievement: AchievementItem) {
    val locked = !achievement.earned
    val progress = if (
        locked && achievement.threshold != null && achievement.threshold > 0 &&
        achievement.currentProgress != null
    ) {
        achievement.currentProgress.toFloat() / achievement.threshold
    } else {
        null
    }
    val cardContainer = if (locked) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = cardContainer),
        border = BorderStroke(
            width = if (achievement.earned) 2.dp else 1.dp,
            color = if (achievement.earned) rarityColor(achievement.rarity) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = if (locked) Icons.Default.Lock else Icons.Default.CheckCircle,
                    contentDescription = if (locked) "Locked" else "Unlocked",
                    tint = if (locked) MaterialTheme.colorScheme.onSurfaceVariant else rarityColor(achievement.rarity),
                    modifier = Modifier.size(30.dp)
                )
                Text(
                    achievement.rarity.replaceFirstChar { it.titlecase(Locale.US) },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                achievement.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                achievement.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                achievement.categoryLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "${achievement.currentProgress} / ${achievement.threshold}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (achievement.earned && !achievement.awardedAt.isNullOrBlank()) {
                Text(
                    "Unlocked ${achievement.awardedAt.take(10)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun rarityColor(rarity: String): Color = when (rarity.lowercase(Locale.US)) {
    "rare" -> Color(0xFFB66A00)
    "epic" -> Color(0xFF6F5CC2)
    "legendary" -> Color(0xFFD49A00)
    else -> MaterialTheme.colorScheme.primary
}
