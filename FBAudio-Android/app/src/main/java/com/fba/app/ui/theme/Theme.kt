package com.fba.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// FBA website brand color: #A85D21
private val FbaBrown = Color(0xFFA85D21)
private val FbaBrownLight = Color(0xFFC47A3A)
private val FbaBrownDark = Color(0xFF7E4518)
private val DarkBrown = Color(0xFF3E2723)

private val LightColorScheme = lightColorScheme(
    primary = FbaBrown,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC2),
    onPrimaryContainer = DarkBrown,
    secondary = FbaBrownLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0C8),
    onSecondaryContainer = DarkBrown,
    tertiary = FbaBrownDark,
    background = Color.White,
    onBackground = Color(0xFF1C1B1F),
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF3EDE8),
    onSurfaceVariant = Color(0xFF52443B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAF6F3),
    surfaceContainer = Color(0xFFF5F0EB),
    surfaceContainerHigh = Color(0xFFEFEAE5),
    surfaceContainerHighest = Color(0xFFE9E4DF),
    outline = Color(0xFF85746A),
    outlineVariant = Color(0xFFD7C9BE),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB77C),
    onPrimary = DarkBrown,
    primaryContainer = FbaBrownDark,
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFFE8B896),
    onSecondary = DarkBrown,
    secondaryContainer = Color(0xFF5A3A24),
    onSecondaryContainer = Color(0xFFFFE0C8),
    tertiary = Color(0xFFD4A373),
    background = Color(0xFF1A1210),
    onBackground = Color(0xFFEDE0D8),
    surface = Color(0xFF1A1210),
    onSurface = Color(0xFFEDE0D8),
    surfaceVariant = Color(0xFF3E302A),
    onSurfaceVariant = Color(0xFFD7C9BE),
    surfaceContainerLowest = Color(0xFF140E0B),
    surfaceContainerLow = Color(0xFF221A16),
    surfaceContainer = Color(0xFF2A201C),
    surfaceContainerHigh = Color(0xFF352B26),
    surfaceContainerHighest = Color(0xFF413630),
    outline = Color(0xFFA08D83),
    outlineVariant = Color(0xFF52443B),
)

@Composable
fun FBATheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
