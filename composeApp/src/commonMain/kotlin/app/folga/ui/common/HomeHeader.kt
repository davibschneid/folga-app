package app.folga.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Header azul da Home. Mostra:
 * - Avatar do usuário (clique leva pra tela de Perfil).
 * - Saudação "Olá, {primeiroNome}".
 * - Chip da equipe (ex.: "Hemato") à direita — só aparece se o usuário
 *   tem equipe cadastrada.
 * - Sino com badge da contagem de trocas pendentes recebidas; o clique
 *   abre a tela de Trocas (onde a lista dessas pendentes fica).
 *
 * A cor azul é fixa (#1E3A8A) pra seguir o mock do cliente, e o
 * `statusBars` é inclusído no padding pra o conteúdo não ficar atrás
 * da barra de status do Android.
 */
@Composable
fun HomeHeader(
    userName: String,
    userTeam: String,
    userPhotoUrl: String?,
    pendingSwapsCount: Int,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    val primary = Color(0xFF1E3A8A)
    Surface(color = primary) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(onClick = onOpenProfile),
            ) {
                ProfileAvatar(
                    name = userName,
                    photoUrl = userPhotoUrl,
                    size = 48.dp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Olá, ${userName.firstName()}",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (userTeam.isNotBlank()) {
                TeamChip(team = userTeam)
                Spacer(Modifier.width(8.dp))
            }
            IconButton(onClick = onOpenNotifications) {
                BadgedBox(
                    badge = {
                        if (pendingSwapsCount > 0) {
                            Badge { Text(pendingSwapsCount.toString()) }
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = "Notificações",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun TeamChip(team: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.18f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Bolinha azul claro — evocação visual do ícone "Hemato" do
            // mock sem precisar de asset externo.
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(Color(0xFF60A5FA), shape = RoundedCornerShape(50)),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = team,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** Pega só o primeiro token do nome pra saudação ficar curta. */
private fun String.firstName(): String {
    val trimmed = trim()
    if (trimmed.isEmpty()) return ""
    return trimmed.substringBefore(' ')
}
