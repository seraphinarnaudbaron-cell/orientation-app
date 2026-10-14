package org.orientationjp.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Green = Color(0xFF1B4332)
val Green2 = Color(0xFF2D6A4F)
val Orange = Color(0xFFF77F00)
val OrangeDark = Color(0xFFD96C00)
val Bg = Color(0xFFF6F7F2)
val Field = Color(0xFFEEF0EA)
val TextDark = Color(0xFF1F2933)
val Muted = Color(0xFF6B7280)
val ErrorRed = Color(0xFFC62828)

@Composable
fun OrientationTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Orange,
            onPrimary = Color.White,
            secondary = Green2,
            onSecondary = Color.White,
            background = Bg,
            onBackground = TextDark,
            surface = Color.White,
            onSurface = TextDark,
        ),
        content = content,
    )
}
