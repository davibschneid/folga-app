package app.folga.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.folga.domain.SwapStatus

/**
 * Badge compacto pra status de troca. Cor muda por status:
 * - ACCEPTED -> verde (confirmada)
 * - PENDING -> laranja (aguardando)
 * - REJECTED -> vermelho (recusada)
 * - CANCELLED -> cinza (cancelada)
 */
@Composable
fun StatusBadge(
    status: SwapStatus,
    modifier: Modifier = Modifier,
) {
    val (label, color) = when (status) {
        SwapStatus.ACCEPTED -> "CONFIRMADA" to Color(0xFF4CAF50)
        SwapStatus.PENDING -> "AGUARDANDO" to Color(0xFFFF9800)
        SwapStatus.REJECTED -> "RECUSADA" to Color(0xFFE53935)
        SwapStatus.CANCELLED -> "CANCELADA" to Color(0xFF757575)
    }
    // Badge mais compacto: padding vertical 6 → 2 e horizontal 12 → 8
    // (reduz altura sem cortar o texto), corner 16 → 12 acompanhando
    // a nova altura, fontSize 11 → 10. Pedido do cliente pra ocupar
    // menos espaço no rodapé do card.
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
