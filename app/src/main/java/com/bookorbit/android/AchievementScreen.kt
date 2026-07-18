package com.bookorbit.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.Undo
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
import androidx.compose.ui.graphics.vector.ImageVector
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
        columns = GridCells.Adaptive(minSize = 128.dp),
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
    val symbolColor = if (locked) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        rarityColor(achievement.rarity)
    }
    val posterColor = if (locked) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    } else {
        rarityColor(achievement.rarity).copy(alpha = 0.16f)
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().aspectRatio(0.72f),
            colors = CardDefaults.cardColors(containerColor = posterColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(14.dp)
            ) {
                Icon(
                    imageVector = achievementIcon(achievement.iconName),
                    contentDescription = "${achievement.name} achievement symbol",
                    tint = symbolColor,
                    modifier = Modifier.size(62.dp).align(Alignment.Center)
                )
                Icon(
                    imageVector = if (locked) Icons.Default.Lock else Icons.Default.CheckCircle,
                    contentDescription = if (locked) "Locked" else "Unlocked",
                    tint = symbolColor,
                    modifier = Modifier.size(22.dp).align(Alignment.TopEnd)
                )
            }
        }
        Text(
            achievement.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            achievement.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "${achievement.categoryLabel} · ${achievement.rarity.replaceFirstChar { it.titlecase(Locale.US) }}",
            style = MaterialTheme.typography.labelSmall,
            color = symbolColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
        } else if (achievement.earned && !achievement.awardedAt.isNullOrBlank()) {
            Text(
                "Unlocked ${achievement.awardedAt.take(10)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal fun achievementIcon(iconName: String): ImageVector = when (iconName.lowercase(Locale.US)) {
    "book-open" -> Icons.Default.MenuBook
    "file-text", "scroll-text", "scroll" -> Icons.Default.Description
    "clock", "timer", "hourglass" -> Icons.Default.AccessTime
    "moon" -> Icons.Default.DarkMode
    "sunrise", "sun" -> Icons.Default.LightMode
    "book-marked", "bookmark-check" -> Icons.Default.Bookmark
    "mountain" -> Icons.Default.Landscape
    "zap" -> Icons.Default.Bolt
    "fast-forward" -> Icons.Default.FastForward
    "library", "files", "folder-open" -> Icons.Default.LocalLibrary
    "highlighter", "pen-line", "pen-tool", "feather" -> Icons.Default.Edit
    "compass", "globe", "orbit" -> Icons.Default.Public
    "sparkles", "party-popper" -> Icons.Default.AutoAwesome
    "calendar-range", "calendar", "calendar-check" -> Icons.Default.Event
    "flame" -> Icons.Default.LocalFireDepartment
    "sprout" -> Icons.Default.Spa
    "corner-up-right" -> Icons.Default.Undo
    "coffee" -> Icons.Default.LocalCafe
    "minimize-2" -> Icons.Default.Minimize
    "arrow-up-down" -> Icons.Default.SwapVert
    "arrow-left-right" -> Icons.Default.SwapHoriz
    "list-ordered" -> Icons.Default.FormatListNumbered
    "heart", "book-heart" -> Icons.Default.Favorite
    "activity", "gauge" -> Icons.Default.Speed
    "repeat" -> Icons.Default.Repeat
    "swords" -> Icons.Default.SportsMma
    "award", "trophy", "medal" -> Icons.Default.EmojiEvents
    "play" -> Icons.Default.PlayArrow
    "rabbit" -> Icons.Default.Pets
    "book-x" -> Icons.Default.Block
    "gavel" -> Icons.Default.Gavel
    "star" -> Icons.Default.Star
    "star-half" -> Icons.Default.StarHalf
    "thumbs-down" -> Icons.Default.ThumbDown
    "palette" -> Icons.Default.Palette
    "smartphone" -> Icons.Default.Smartphone
    else -> Icons.Default.EmojiEvents
}

@Composable
private fun rarityColor(rarity: String): Color = when (rarity.lowercase(Locale.US)) {
    "rare" -> Color(0xFFB66A00)
    "epic" -> Color(0xFF6F5CC2)
    "legendary" -> Color(0xFFD49A00)
    else -> MaterialTheme.colorScheme.primary
}
