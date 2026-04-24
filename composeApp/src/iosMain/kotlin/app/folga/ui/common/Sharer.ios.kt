package app.folga.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Stub iOS: por ora não compartilha nada. Quando priorizarmos iOS, o
 * actual real vai abrir um `UIActivityViewController` com o texto.
 */
@Composable
actual fun rememberSharer(): (subject: String, body: String) -> Unit =
    remember { { _, _ -> /* no-op no iOS por enquanto */ } }
