package com.bookorbit.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookOrbitTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = "Lagrange logo",
                    modifier = Modifier.size(28.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = "LAGRANGE",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = "a BookOrbit reader",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(text = title, style = MaterialTheme.typography.titleLarge)
                }
            }
        },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.primary,
            actionIconContentColor = MaterialTheme.colorScheme.primary
        )
    )
}

internal enum class OrbitMessageTone {
    INFO,
    ERROR,
    OFFLINE
}

@Composable
internal fun OrbitMessage(
    text: String,
    modifier: Modifier = Modifier,
    tone: OrbitMessageTone = OrbitMessageTone.INFO
) {
    val containerColor = when (tone) {
        OrbitMessageTone.INFO -> MaterialTheme.colorScheme.primaryContainer
        OrbitMessageTone.ERROR -> MaterialTheme.colorScheme.errorContainer
        OrbitMessageTone.OFFLINE -> MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = when (tone) {
        OrbitMessageTone.INFO -> MaterialTheme.colorScheme.onPrimaryContainer
        OrbitMessageTone.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        OrbitMessageTone.OFFLINE -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val markerColor = when (tone) {
        OrbitMessageTone.INFO -> MaterialTheme.colorScheme.primary
        OrbitMessageTone.ERROR -> MaterialTheme.colorScheme.error
        OrbitMessageTone.OFFLINE -> MaterialTheme.colorScheme.secondary
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(8.dp)
                    .background(markerColor, CircleShape)
            )
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun OrbitEyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.labelSmall
    )
}
