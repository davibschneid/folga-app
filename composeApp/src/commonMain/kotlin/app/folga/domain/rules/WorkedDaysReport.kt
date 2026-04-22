package app.folga.domain.rules

import app.folga.domain.Folga
import app.folga.domain.SwapRequest
import app.folga.domain.SwapStatus
import app.folga.domain.User
import kotlinx.datetime.LocalDate

/**
 * Linha do relatório de dias trabalhados considerando trocas. Cada linha
 * representa um colaborador no período selecionado.
 *
 * No modelo atual do app a "escala original" não é persistida — o app só
 * conhece as folgas que o usuário cadastrou + as trocas. Por isso o
 * relatório não tem a coluna "dias originais da escala"; mostra apenas o
 * impacto das trocas aceitas no período (útil para fechamento, ajuste de
 * pagamento de dias a mais/a menos etc.).
 */
data class WorkedDaysReportRow(
    val user: User,
    /** Quantos dias o usuário **cedeu** (foi requester em trocas aceitas). */
    val cededDays: Int,
    /** Quantos dias o usuário **assumiu** (foi target em trocas aceitas). */
    val assumedDays: Int,
) {
    /**
     * Saldo líquido de dias no período: positivo = trabalhou mais do que
     * o originalmente escalado; negativo = trabalhou menos. Útil pro
     * gestor calcular diferença de pagamento.
     */
    val balance: Int get() = assumedDays - cededDays
}

/**
 * Gera o relatório para o período `[from, to]` (inclusive nas duas pontas).
 *
 * **Regras aplicadas:**
 *  - Só trocas com status `ACCEPTED` entram. `PENDING`, `REJECTED` e
 *    `CANCELLED` são ignoradas.
 *  - A data usada pra enquadrar a troca no período é a data da folga
 *    cedida (`fromFolgaId.date`). Se a folga não for encontrada (ex.:
 *    doc removido do Firestore), a troca é ignorada silenciosamente pra
 *    não quebrar o relatório.
 *  - Idempotência: a função é pura — chamar duas vezes com os mesmos
 *    inputs dá o mesmo resultado. Nada é persistido.
 *
 * **Nota sobre o modelo unidirecional:** cada troca aceita causa
 * exatamente **-1 dia** pro `requesterId` e **+1 dia** pro `targetId`.
 * Não há dia de contrapartida.
 */
fun buildWorkedDaysReport(
    users: List<User>,
    folgas: List<Folga>,
    swaps: List<SwapRequest>,
    from: LocalDate,
    to: LocalDate,
): List<WorkedDaysReportRow> {
    if (from > to) return emptyList()
    val folgaById = folgas.associateBy { it.id }
    val accepted = swaps.filter { it.status == SwapStatus.ACCEPTED }
        .filter { swap ->
            val date = folgaById[swap.fromFolgaId]?.date ?: return@filter false
            date in from..to
        }
    val cededByUser = accepted.groupingBy { it.requesterId }.eachCount()
    val assumedByUser = accepted.groupingBy { it.targetId }.eachCount()
    return users
        .map { user ->
            WorkedDaysReportRow(
                user = user,
                cededDays = cededByUser[user.id] ?: 0,
                assumedDays = assumedByUser[user.id] ?: 0,
            )
        }
        .sortedBy { it.user.name.lowercase() }
}
