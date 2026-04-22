package app.folga.domain.rules

import app.folga.domain.Shift
import app.folga.domain.SwapRequest
import app.folga.domain.SwapStatus
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Business rules para troca de folga. Mantidas em `domain/rules/` pra ficarem
 * reutilizáveis e testáveis sem dependência de Firebase/Compose.
 *
 * Definidas pelo cliente (Davi):
 *  - Quota de trocas por *período* (custom, dia 16 do mês até dia 15 do mês seguinte):
 *      * MANHA / TARDE → 4 trocas
 *      * NOITE → 3 trocas
 *    Só conta troca **aceita** onde o usuário foi o **iniciador** (requester).
 *    O alvo (target) da troca NÃO consome quota do próprio período.
 *  - Ao solicitar troca, o app **bloqueia** quando a quota já foi atingida
 *    (antes era aviso não-bloqueante — comportamento mudou no PR #16). O
 *    botão "Solicitar troca" fica desabilitado e `SwapsViewModel.requestSwap()`
 *    rejeita a submissão até o próximo período (dia 16).
 *  - (Pendente) Regra de 2 plantões seguidos do noturno: depende de como a
 *    escala de plantão vai ser modelada. Adiada até decisão do cliente.
 */

/**
 * Janela de contagem de trocas. Corresponde ao período "dia 16 do mês atual
 * → dia 15 do próximo mês, inclusive nas duas pontas".
 */
data class SwapPeriod(val start: LocalDate, val endInclusive: LocalDate) {
    fun contains(date: LocalDate): Boolean = date in start..endInclusive

    fun contains(instant: Instant, zone: TimeZone): Boolean =
        contains(instant.toLocalDateTime(zone).date)
}

/**
 * Período corrente baseado em [now]. Se hoje é dia ≥ 16, o período começa
 * hoje dia 16 e termina dia 15 do próximo mês. Se hoje é dia < 16, o período
 * começou dia 16 do mês passado e termina dia 15 do mês atual.
 */
fun currentSwapPeriod(
    now: Instant,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): SwapPeriod {
    val today = now.toLocalDateTime(zone).date
    val start = if (today.dayOfMonth >= 16) {
        LocalDate(today.year, today.monthNumber, 16)
    } else {
        // Dia 16 do mês anterior. LocalDate.minus(1, DateTimeUnit.MONTH)
        // evita edge cases de janeiro → dezembro do ano anterior.
        val prev = LocalDate(today.year, today.monthNumber, 1)
            .minus(1, DateTimeUnit.MONTH)
        LocalDate(prev.year, prev.monthNumber, 16)
    }
    // Dia 15 do mês seguinte ao start. Usa plus(1, MONTH) pra não errar em
    // meses de 28/30/31 dias ou na virada de ano.
    val nextMonth = start.plus(1, DateTimeUnit.MONTH)
    val endInclusive = LocalDate(nextMonth.year, nextMonth.monthNumber, 15)
    return SwapPeriod(start, endInclusive)
}

/** Quantas trocas o usuário pode iniciar no período, segundo seu turno. */
fun swapQuotaFor(shift: Shift): Int = when (shift) {
    Shift.MANHA, Shift.TARDE -> 4
    Shift.NOITE -> 3
}

/**
 * Conta quantas trocas ACEITAS o usuário iniciou (foi o requester) no período.
 * Aceitas apenas — pendentes/rejeitadas/canceladas não entram. Só quem iniciou
 * a troca consome quota; o alvo não.
 *
 * Usa `respondedAt` quando disponível (momento em que a troca virou ACCEPTED),
 * caindo pra `createdAt` como fallback defensivo.
 */
fun countAcceptedInitiatedSwaps(
    userId: String,
    swaps: List<SwapRequest>,
    period: SwapPeriod,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): Int = swaps.count { swap ->
    swap.requesterId == userId &&
        swap.status == SwapStatus.ACCEPTED &&
        (swap.respondedAt ?: swap.createdAt).let { period.contains(it, zone) }
}
