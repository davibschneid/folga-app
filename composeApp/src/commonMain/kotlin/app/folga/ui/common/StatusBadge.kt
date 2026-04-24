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
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = color,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
