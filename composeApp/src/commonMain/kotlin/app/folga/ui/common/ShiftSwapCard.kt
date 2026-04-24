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
 * Card de "Troca de Turno" usado tanto na Home quanto na tela de Trocas.
 * Layout inspirado no snippet enviado pelo cliente: dois avatares de
 * perfil separados por um ícone de seta dupla, nomes logo abaixo, linha
 * com turnos e badge de status no rodapé.
 *
 * Estrutura:
 * - Header: título "Troca de Turno" + data à direita
 * - Row dos avatares + nomes + seta
 * - Linha "Fulano: Manhã | Ciclano: Tarde" (se ambos turnos estão
 *   preenchidos; senão omite)
 * - Badge de status
 */
@Composable
fun ShiftSwapCard(
    requesterName: String,
    requesterPhotoUrl: String?,
    requesterShift: Shift?,
    targetName: String,
    targetPhotoUrl: String?,
    targetShift: Shift?,
    date: LocalDate?,
    status: SwapStatus,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Troca de Turno",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = date?.let { formatShort(it) } ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
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
            if (requesterShift != null && targetShift != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$requesterName: ") }
                        append(shiftLabel(requesterShift))
                        append("  |  ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$targetName: ") }
                        append(shiftLabel(targetShift))
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatusBadge(status = status)
                if (actions != null) actions()
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
        ProfileAvatar(name = name, photoUrl = photoUrl, size = 40.dp)
        Spacer(Modifier.height(6.dp))
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

/** Formato "DD/MM". O ano fica de fora pra economizar espaço. */
private fun formatShort(date: LocalDate): String {
    val d = date.dayOfMonth.toString().padStart(2, '0')
    val m = date.monthNumber.toString().padStart(2, '0')
    return "$d/$m"
}
