package com.fba.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val SaffronOrange = Color(0xFFE8891D)
private val DeepSaffron = Color(0xFFC5711A)
private val WarmGold = Color(0xFFF5C842)
private val DarkBrown = Color(0xFF3E2723)
private val Cream = Color(0xFFFFF8E1)

private val LightColorScheme = lightColorScheme(
    primary = SaffronOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDB3),
    onPrimaryContainer = DarkBrown,
    secondary = DeepSaffron,
    onSecondary = Color.White,
    tertiary = WarmGold,
    background = Cream,
    surface = Color.White,
    onBackground = DarkBrown,
    onSurface = DarkBrown,
)

private val DarkColorScheme = darkColorScheme(
    primary = WarmGold,
    onPrimary = DarkBrown,
    primaryContainer = DeepSaffron,
    onPrimaryContainer = Color(0xFFFFDDB3),
    secondary = SaffronOrange,
    onSecondary = DarkBrown,
    tertiary = WarmGold,
    background = Color(0xFF1A1210),
    surface = Color(0xFF2A201C),
    onBackground = Cream,
    onSurface = Cream,
)

@Composable
fun FBATheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
