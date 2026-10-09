package com.machine.newsapp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val palette = darkColorScheme(
    primary = Color(0xFFA3E4D7),
    onPrimary = Color(0xFF06382F),
    primaryContainer = Color(0xFF183D35),
    onPrimaryContainer = Color(0xFFC7F5E8),
    secondary = Color(0xFFD7C5A0),
    background = Color(0xFF0E1317),
    onBackground = Color(0xFFF1F1EB),
    surface = Color(0xFF151D22),
    surfaceContainer = Color(0xFF1B252B),
    onSurface = Color(0xFFF1F1EB),
    onSurfaceVariant = Color(0xFFAAB8BD),
    outline = Color(0xFF43525A),
    error = Color(0xFFFFB4AB),
)

@Composable
fun NewsTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = palette, content = content) }
