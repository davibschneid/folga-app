package app.folga.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext

/**
 * Actual do Android: usa Coil `AsyncImage` pra carregar a URL (tipicamente
 * vinda do Firebase Storage). Enquanto a imagem carrega ou se o load
 * falhar (URL expirada, sem rede), o `fallback` é desenhado no lugar —
 * fica empilhado no mesmo `Box` pra evitar salto de layout.
 */
@Composable
actual fun RemoteImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier,
    fallback: @Composable () -> Unit,
) {
    var loaded by remember(url) { mutableStateOf(false) }
    var errored by remember(url) { mutableStateOf(false) }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (!loaded || errored) fallback()
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
            onSuccess = { loaded = true; errored = false },
            onError = { errored = true },
            onLoading = { loaded = false; errored = false },
        )
    }
}
