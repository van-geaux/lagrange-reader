package com.bookorbit.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun BookOrbitTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        if (androidx.compose.foundation.isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (androidx.compose.foundation.isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
