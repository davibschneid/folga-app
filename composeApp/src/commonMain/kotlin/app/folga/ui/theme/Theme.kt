package app.folga.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6BFF),
    onPrimary = Color.White,
    secondary = Color(0xFF00B894),
    background = Color(0xFFF6F7FB),
    surface = Color.White,
)

@Composable
fun FolgaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
