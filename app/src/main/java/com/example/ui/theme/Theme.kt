package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = LimeSprout,
    onPrimary = ObsidianBlack,
    primaryContainer = Cyprus,
    onPrimaryContainer = Sand,
    secondary = CyprusLight,
    onSecondary = Sand,
    tertiary = FreshCanopyLight,
    onTertiary = Sand,
    background = ObsidianBlack,
    onBackground = Sand,
    surface = ObsidianSurface,
    onSurface = Sand,
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = SandSecondary,
    error = AlertRed,
    onError = ObsidianBlack
)

@Composable
fun ZyloVeilTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = ObsidianBlack.toArgb()
                window.navigationBarColor = ObsidianBlack.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
