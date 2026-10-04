package de.madeat545.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object C {
    val Creme = Color(0xFFFAF6F0)
    val Beige = Color(0xFFEFE6DA)
    val Sand = Color(0xFFD2B48C)
    val Rose = Color(0xFFE6C3B3)
    val Mokka = Color(0xFF4A3B30)
    val MokkaLight = Color(0xFF8A7666)
    val Line = Color(0xFFE3D6C4)
}

@Composable
fun MadeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = C.Sand,
            onPrimary = C.Mokka,
            secondary = C.Rose,
            onSecondary = C.Mokka,
            background = C.Creme,
            onBackground = C.Mokka,
            surface = C.Beige,
            onSurface = C.Mokka,
        ),
        content = content,
    )
}
