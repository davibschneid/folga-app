package app.folga.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Avatar circular do usuário. Se houver [photoUrl], renderiza a foto
 * carregada via [RemoteImage] (Coil no Android); caso contrário mostra
 * as iniciais do nome sobre um círculo na cor `primary`. Usado no header
 * da Home, nos cards de troca e na tela de Perfil.
 */
@Composable
fun ProfileAvatar(
    name: String,
    photoUrl: String? = null,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
) {
    val initials = name.toInitials()
    val fallback: @Composable () -> Unit = {
        InitialsAvatar(initials = initials, size = size)
    }
    if (photoUrl.isNullOrBlank()) {
        Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
            fallback()
        }
    } else {
        RemoteImage(
            url = photoUrl,
            contentDescription = "Foto de $name",
            modifier = modifier.size(size).clip(CircleShape),
            fallback = fallback,
        )
    }
}

@Composable
private fun InitialsAvatar(initials: String, size: Dp) {
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initials,
                // Escala o tamanho da fonte com o avatar — 18sp em 48dp
                // fica bem legível; proporção mantida pra avatares
                // maiores (perfil) e menores (cards).
                fontSize = (size.value * 0.38f).sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Pega até duas iniciais do nome — primeiro + último token, ignorando
 * tokens vazios e acentos. Nome em branco vira "?".
 */
private fun String.toInitials(): String {
    val tokens = trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        tokens.isEmpty() -> "?"
        tokens.size == 1 -> tokens[0].take(1).uppercase()
        else -> (tokens.first().take(1) + tokens.last().take(1)).uppercase()
    }
}
