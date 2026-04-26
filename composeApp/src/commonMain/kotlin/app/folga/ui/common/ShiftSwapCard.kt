package app.folga.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import app.folga.domain.Shift
import app.folga.domain.SwapStatus
import kotlinx.datetime.LocalDate

/**
 * Card de "Troca de trabalho" usado tanto na Home quanto na tela de
 * Trocas. Layout inspirado no snippet do cliente: dois avatares de
 * perfil separados por um ícone de seta dupla, nomes logo abaixo, linha
 * descritiva da troca e badge de status no rodapé.
 *
 * Estrutura (compacta — alturas de padding/spacer reduzidas em ~25%
 * em relação ao layout original pra caberem mais cards na home sem
 * scroll):
 * - Header: título "Troca de trabalho" + data à direita
 * - Row dos avatares + nomes + seta
 * - Linha descritiva sensível ao status e à perspectiva do usuário
 *   atual (requester ou target) — montada por [swapDescription]
 * - Badge de status
 *
 * `viewerRole` define se o usuário atual é o requester ou target da
 * troca, ou `null` (visualização neutra/admin) — controla as
 * mensagens "para você"/"de você" da linha descritiva.
 *
 * Os parâmetros `requesterShift`/`targetShift` ficam disponíveis na
 * assinatura mas não são exibidos — a linha de turno foi removida
 * (turno é regra de negócio interna, não interessa ao usuário
 * lendo a troca).
 */
@Composable
fun ShiftSwapCard(
    requesterName: String,
    requesterPhotoUrl: String?,
    @Suppress("UNUSED_PARAMETER") requesterShift: Shift?,
    targetName: String,
    targetPhotoUrl: String?,
    @Suppress("UNUSED_PARAMETER") targetShift: Shift?,
    date: LocalDate?,
    status: SwapStatus,
    viewerRole: SwapViewerRole?,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Troca de trabalho",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = date?.let { formatShort(it) } ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ParticipantColumn(
                    name = requesterName,
                    photoUrl = requesterPhotoUrl,
                    modifier = Modifier.weight(1f),
                    alignment = Alignment.Start,
                )
                SwapArrowBadge()
                ParticipantColumn(
                    name = targetName,
                    photoUrl = targetPhotoUrl,
                    modifier = Modifier.weight(1f),
                    alignment = Alignment.End,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = swapDescription(
                    status = status,
                    requesterName = requesterName,
                    targetName = targetName,
                    date = date,
                    viewerRole = viewerRole,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            // Status badge sempre numa linha só (à esquerda). Quando o
            // card tem ações (Aceitar/Recusar/Cancelar), elas vão numa
            // linha separada abaixo do badge — evita quebra do label
            // "Recusar" em telas estreitas (4xx px) onde StatusBadge +
            // 2 botões não caberiam lado a lado no mesmo Row.
            StatusBadge(status = status)
            if (actions != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    actions()
                }
            }
        }
    }
}

@Composable
private fun ParticipantColumn(
    name: String,
    photoUrl: String?,
    modifier: Modifier,
    alignment: Alignment.Horizontal,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = alignment,
    ) {
        // Avatar reduzido (40 → 36) acompanhando o redesign mais compacto
        // do card. Spacer entre avatar e nome também reduzido.
        ProfileAvatar(name = name, photoUrl = photoUrl, size = 36.dp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun SwapArrowBadge() {
    Surface(
        modifier = Modifier.size(32.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.SwapHoriz,
                contentDescription = "Troca",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun shiftLabel(shift: Shift): String = when (shift) {
    Shift.MANHA -> "Manhã"
    Shift.TARDE -> "Tarde"
    Shift.NOITE -> "Noite"
}

/**
 * Perspectiva do usuário que está olhando o card. Define como a linha
 * descritiva é fraseada — "<colega> aceitou trabalhar para você" só faz
 * sentido se o viewer é o requester; se é o target, vira "Você aceitou
 * trabalhar para <colega>".
 */
enum class SwapViewerRole { REQUESTER, TARGET }

/**
 * Constrói a linha descritiva do card baseada no status da troca e na
 * perspectiva do usuário (requester / target / neutro).
 *
 * Copy revisado pelo cliente:
 *  - PENDING: "Aguardando o usuário <B>" / "Aguardando sua resposta…"
 *  - ACCEPTED: "<A> aceitou trabalhar para você no dia DD/MM"
 *  - REJECTED: "Proposta recusada por <A> no dia DD/MM"
 *  - CANCELLED: "<A> cancelou o trabalho agendado (DD/MM)"
 *
 * Onde:
 *  - <A> em ACCEPTED/REJECTED é o **target** (quem aceitou/recusou).
 *  - <A> em CANCELLED é o **requester** (só ele cancela).
 *  - <B> em PENDING é o **target** (a quem está sendo pedido).
 *
 * Quando o viewer é o lado <A>/<B>, a frase é flexionada na 1ª pessoa
 * ("Você aceitou…", "Aguardando sua resposta…") pra ficar mais natural.
 */
internal fun swapDescription(
    status: SwapStatus,
    requesterName: String,
    targetName: String,
    date: LocalDate?,
    viewerRole: SwapViewerRole?,
): androidx.compose.ui.text.AnnotatedString {
    val dateStr = date?.let { formatShort(it) } ?: "—"
    return buildAnnotatedString {
        when (status) {
            SwapStatus.PENDING -> when (viewerRole) {
                SwapViewerRole.REQUESTER -> {
                    append("Aguardando o usuário ")
                    bold(targetName)
                }
                SwapViewerRole.TARGET -> {
                    append("Aguardando sua resposta — ")
                    bold(requesterName)
                    append(" pediu o dia $dateStr")
                }
                null -> {
                    append("Aguardando ")
                    bold(targetName)
                    append(" responder — pedido de ")
                    bold(requesterName)
                }
            }

            SwapStatus.ACCEPTED -> when (viewerRole) {
                SwapViewerRole.REQUESTER -> {
                    bold(targetName)
                    append(" aceitou trabalhar para você no dia $dateStr")
                }
                SwapViewerRole.TARGET -> {
                    append("Você aceitou trabalhar para ")
                    bold(requesterName)
                    append(" no dia $dateStr")
                }
                null -> {
                    bold(targetName)
                    append(" aceitou trabalhar para ")
                    bold(requesterName)
                    append(" no dia $dateStr")
                }
            }

            SwapStatus.REJECTED -> when (viewerRole) {
                SwapViewerRole.REQUESTER -> {
                    append("Proposta recusada por ")
                    bold(targetName)
                    append(" no dia $dateStr")
                }
                SwapViewerRole.TARGET -> {
                    append("Você recusou a proposta de ")
                    bold(requesterName)
                    append(" no dia $dateStr")
                }
                null -> {
                    bold(targetName)
                    append(" recusou a proposta de ")
                    bold(requesterName)
                    append(" no dia $dateStr")
                }
            }

            // CANCELLED só é alcançável pelo requester (regra de
            // negócio + Firestore rules), então a perspectiva
            // determina se mostramos "Você cancelou" ou
            // "<requester> cancelou".
            SwapStatus.CANCELLED -> when (viewerRole) {
                SwapViewerRole.REQUESTER -> {
                    append("Você cancelou o trabalho agendado ($dateStr)")
                }
                SwapViewerRole.TARGET -> {
                    bold(requesterName)
                    append(" cancelou o trabalho agendado ($dateStr)")
                }
                null -> {
                    bold(requesterName)
                    append(" cancelou o trabalho agendado ($dateStr)")
                }
            }
        }
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.bold(text: String) {
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text) }
}

/** Formato "DD/MM". O ano fica de fora pra economizar espaço. */
private fun formatShort(date: LocalDate): String {
    val d = date.dayOfMonth.toString().padStart(2, '0')
    val m = date.monthNumber.toString().padStart(2, '0')
    return "$d/$m"
}
