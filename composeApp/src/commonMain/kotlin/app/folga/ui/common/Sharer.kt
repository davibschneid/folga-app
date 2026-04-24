package app.folga.ui.common

import androidx.compose.runtime.Composable

/**
 * Launcher pra abrir o share sheet nativo da plataforma com um payload
 * de texto. O composable [rememberSharer] devolve uma função que, quando
 * chamada com `(subject, body)`, abre o seletor de apps de
 * compartilhamento (WhatsApp, Gmail, Drive, etc.).
 *
 * Implementação platform-specific:
 * - androidMain: `Intent.ACTION_SEND` com `EXTRA_SUBJECT` + `EXTRA_TEXT`
 *   embrulhado em `Intent.createChooser` pra mostrar a lista de apps.
 * - iosMain: stub no-op por enquanto (iOS não está priorizado).
 */
@Composable
expect fun rememberSharer(): (subject: String, body: String) -> Unit
