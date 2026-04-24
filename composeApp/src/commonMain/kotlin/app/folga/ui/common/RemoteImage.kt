package app.folga.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Renderiza uma imagem remota (URL) dentro do `modifier` recebido — usado
 * pra mostrar a foto de perfil vinda do Firebase Storage. O `fallback` é
 * o composable renderizado enquanto a imagem carrega ou se der erro
 * (tipicamente o `ProfileAvatar` com iniciais).
 *
 * Implementação platform-specific via expect/actual:
 * - androidMain: Coil `AsyncImage`
 * - iosMain: por enquanto só delega pro `fallback` (KMP de imagens remotas
 *   sem adicionar peso no bundle iOS — pode ser trocado por NSURLSession
 *   quando for priorizar o target iOS).
 */
@Composable
expect fun RemoteImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit,
)
