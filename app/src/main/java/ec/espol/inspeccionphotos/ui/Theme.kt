package ec.espol.inspeccionphotos.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SpatialBlue = Color(0xFF2D8CFF)
val SpatialTop = Color(0xFF06396F)
val SpatialBg = Color(0xFF07111F)
val SpatialPanel = Color(0xFF111D31)
val SpatialCard = Color(0xFF182640)
val SpatialBorder = Color(0xFF2D4161)
val SpatialMuted = Color(0xFFAEB9CC)
val SpatialGood = Color(0xFF66D19E)
val SpatialWarn = Color(0xFFF6C768)
val SpatialBad = Color(0xFFFF7C7C)

@Composable
fun InspeccionPhotosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = SpatialBlue,
            background = SpatialBg,
            surface = SpatialCard,
            onPrimary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}
