package app.folga.ui.swap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folga.domain.Folga
import app.folga.domain.FolgaStatus
import app.folga.domain.Shift
import app.folga.domain.SwapRequest
import app.folga.domain.SwapStatus
import app.folga.domain.User
import app.folga.ui.common.AppBottomBar
import app.folga.ui.common.MainTab
import app.folga.ui.common.ShiftSwapCard
import app.folga.ui.common.formatBrazilian
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapsScreen(
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    viewModel: SwapsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val my by viewModel.myFolgas.collectAsStateWithLifecycle()
    val allFolgas by viewModel.allFolgas.collectAsStateWithLifecycle()
    val awaiting by viewModel.folgaIdsAwaiting.collectAsStateWithLifecycle()
    val colleagues by viewModel.colleagues.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val incoming by viewModel.incoming.collectAsStateWithLifecycle()
    val outgoing by viewModel.outgoing.collectAsStateWithLifecycle()
    val quota by viewModel.quotaStatus.collectAsStateWithLifecycle()
    val me by viewModel.currentUser.collectAsStateWithLifecycle()

    // Badge da aba Trocas: mesma lógica do Home, conta só os pendentes
    // que chegaram pra mim. Derivado direto da lista já carregada.
    val pendingCount = incoming.count { it.status == SwapStatus.PENDING }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trocar dia de trabalho") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Voltar", color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E3A8A),
                    titleContentColor = Color.White,
                ),
            )
        },
        bottomBar = {
            AppBottomBar(
                selected = MainTab.SWAPS,
                pendingSwapsCount = pendingCount,
                onSelectHome = onBack,
                onSelectSwaps = {},
                onSelectProfile = onOpenProfile,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Aviso pra noturno: regra de negócio "até 2 plantões seguidos".
            // Mostrado em vermelho como flag visível antes de o noturno
            // selecionar dia/colega — chama atenção pra um limite que não
            // tem como o app validar (depende da escala) mas que o usuário
            // precisa lembrar.
            if (me?.shift == Shift.NOITE) {
                Text(
                    text = "Permitido trabalho em até 2 plantões seguidos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
            }
            quota?.let { q ->
                // Chip informativo com a quota restante. Cor muda quando
                // o usuário atinge o limite, pra deixar óbvio que novas
                // solicitações vão ser bloqueadas.
                AssistChip(
                    onClick = {},
                    label = { Text(quotaChipLabel(q.shift, q.remaining, q.quota)) },
                    colors = if (q.atOrAboveQuota) {
                        AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            labelColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    } else AssistChipDefaults.assistChipColors(),
                )
                Spacer(Modifier.height(8.dp))
            }

            Text("Solicitar troca", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                // Explicação curta do modelo pra o usuário entender o que
                // acontece depois do aceite. Modelo unidirecional: o colega
                // escolhido passa a trabalhar no dia que o usuário selecionou
                // (o usuário fica sem esse dia de trabalho).
                text = "Selecione um dia seu e escolha um colega. Se ele " +
                    "aceitar, vai trabalhar no dia selecionado no seu lugar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            Text("Meus dias cadastrados", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            FolgaChips(
                // Ordenamos por data (ascendente — próximo primeiro)
                // pra "Meus dias cadastrados" também ficar em ordem
                // cronológica aqui na tela de Trocas, mesmo padrão da
                // Home (`FolgasScreen`).
                folgas = my.filter { it.status == FolgaStatus.SCHEDULED }
                    .sortedBy { it.date },
                selectedId = state.selectedMyFolgaId,
                onSelect = viewModel::selectMy,
                users = users,
                awaitingIds = awaiting,
            )

            Spacer(Modifier.height(12.dp))
            Text("Escolha um colega", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                // Regra de negócio: só aparecem colegas do mesmo grupo de
                // turno do usuário (diurno MANHA/TARDE ou noturno NOITE).
                text = "A lista mostra apenas colegas do mesmo grupo de " +
                    "turno (diurno ou noturno).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            UserChips(
                users = colleagues,
                selectedId = state.selectedTargetUserId,
                onSelect = viewModel::selectTargetUser,
            )

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.message,
                onValueChange = viewModel::onMessageChange,
                label = { Text("Mensagem (opcional)") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))
            val atLimit = quota?.atOrAboveQuota == true
            Button(
                onClick = viewModel::requestSwap,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading && !atLimit,
            ) {
                Text(
                    if (atLimit) "Limite de trocas atingido no período"
                    else "Solicitar troca",
                )
            }

            // Listas de Recebidas/Enviadas usam o mesmo `ShiftSwapCard`
            // das trocas agendadas da Home pra manter o layout coeso
            // (pedido do cliente). O slot de `actions` recebe os botões
            // específicos de cada lista (Aceitar/Recusar pras recebidas,
            // Cancelar pras enviadas). Passamos `allFolgas` (não só as
            // minhas) pra a data resolver corretamente em incoming
            // (fromFolgaId é do requester) e em outgoing já aceita (a
            // folga foi transferida pro target).
            // Ordenamos Recebidas e Enviadas pela data da folga em
            // questão (ascendente — a próxima a acontecer primeiro).
            // A data vem de `allFolgas` via `fromFolgaId`. Trocas
            // sem folga resolvida (fromFolgaId não está em allFolgas)
            // ficam no fim — usamos uma data sentinel bem distante,
            // mesmo padrão do `FolgasViewModel.byDateOnly`. Sem o
            // sentinel, `sortedBy` colocaria os nulls no início porque
            // `compareValues` trata null como < qualquer não-null.
            val folgaDateById = allFolgas.associate { it.id to it.date }
            val sortKey: (SwapRequest) -> LocalDate = { swap ->
                folgaDateById[swap.fromFolgaId] ?: LocalDate(9999, 12, 31)
            }
            val sortedIncoming = incoming.sortedBy(sortKey)
            val sortedOutgoing = outgoing.sortedBy(sortKey)

            Spacer(Modifier.height(24.dp))
            Text("Recebidas", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (sortedIncoming.isEmpty()) Text("Nenhuma solicitação recebida.", style = MaterialTheme.typography.bodySmall)
            sortedIncoming.forEach { swap ->
                SwapCardWithActions(
                    swap = swap,
                    users = users,
                    folgas = allFolgas,
                    actions = {
                        if (swap.status == SwapStatus.PENDING) {
                            // Espaçamento entre os botões vem do
                            // `Row(spacedBy=8.dp)` dentro do
                            // `ShiftSwapCard` — Spacer manual aqui
                            // somaria com o spacedBy e geraria 24dp.
                            Button(onClick = { viewModel.accept(swap.id) }) { Text("Aceitar") }
                            OutlinedButton(onClick = { viewModel.reject(swap.id) }) { Text("Recusar") }
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(16.dp))
            Text("Enviadas", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (sortedOutgoing.isEmpty()) Text("Nenhuma solicitação enviada.", style = MaterialTheme.typography.bodySmall)
            sortedOutgoing.forEach { swap ->
                SwapCardWithActions(
                    swap = swap,
                    users = users,
                    folgas = allFolgas,
                    actions = {
                        if (swap.status == SwapStatus.PENDING) {
                            OutlinedButton(onClick = { viewModel.cancel(swap.id) }) { Text("Cancelar") }
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

}

private fun quotaChipLabel(shift: Shift, remaining: Int, quota: Int): String =
    "Trocas restantes: $remaining de $quota · ${shiftLabel(shift)}"

private fun shiftLabel(shift: Shift): String = when (shift) {
    Shift.MANHA -> "Manhã"
    Shift.TARDE -> "Tarde"
    Shift.NOITE -> "Noite"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FolgaChips(
    folgas: List<Folga>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    users: List<User>,
    awaitingIds: Set<String>,
) {
    if (folgas.isEmpty()) {
        Text("Nenhum dia disponível.", style = MaterialTheme.typography.bodySmall)
        return
    }
    // FlowRow quebra as chips pra linhas adicionais quando não cabem na
    // largura da tela — evita corte horizontal quando o usuário tem vários
    // dias cadastrados. Um `Row` simples não quebra e os chips em excesso
    // ficam invisíveis ou espremidos.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        folgas.forEach { folga ->
            val owner = users.firstOrNull { it.id == folga.userId }?.name ?: "—"
            // Se o dia tem troca pendente, marcamos visualmente com
            // " · Aguardando" em laranja no fim do label e bloqueamos
            // a seleção (`enabled = false`). O M3 FilterChip cinza-out
            // automaticamente quando `enabled = false`, então o usuário
            // entende que aquela chip não é interativa. Pedido do
            // cliente: deixar claro que o dia já tem solicitação em
            // aberto e impedir nova solicitação pra ele.
            val pending = folga.id in awaitingIds
            FilterChip(
                selected = selectedId == folga.id,
                onClick = { onSelect(folga.id) },
                enabled = !pending,
                label = {
                    if (pending) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${folga.date.formatBrazilian()} · $owner")
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Aguardando",
                                color = Color(0xFFFF8A00),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    } else {
                        Text("${folga.date.formatBrazilian()} · $owner")
                    }
                },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

/**
 * Lista de colegas em formato de FilterChip. No modelo unidirecional novo
 * o usuário seleciona *um colega* (não mais um dia específico do colega)
 * pra assumir o dia que ele cadastrou. Por isso aqui a chip mostra só o
 * nome — não tem data nem status porque o colega não precisou cadastrar
 * nada pro pedido existir.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun UserChips(
    users: List<User>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    if (users.isEmpty()) {
        Text("Nenhum colega disponível.", style = MaterialTheme.typography.bodySmall)
        return
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        users.forEach { user ->
            FilterChip(
                selected = selectedId == user.id,
                onClick = { onSelect(user.id) },
                label = { Text(user.name) },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

/**
 * Wrapper que monta os parâmetros do [ShiftSwapCard] a partir do
 * [SwapRequest] cru — resolve nomes/fotos/turnos no [users] e a data
 * (folga.date) no [folgas]. `folgas` precisa ser a lista global do
 * sistema porque o `fromFolgaId` pode pertencer a outro usuário
 * (incoming) ou ter sido transferido (outgoing já aceita).
 */
@Composable
private fun SwapCardWithActions(
    swap: SwapRequest,
    users: List<User>,
    folgas: List<Folga>,
    actions: @Composable () -> Unit,
) {
    val requester = users.firstOrNull { it.id == swap.requesterId }
    val target = users.firstOrNull { it.id == swap.targetId }
    val date = folgas.firstOrNull { it.id == swap.fromFolgaId }?.date
    ShiftSwapCard(
        requesterName = requester?.name ?: swap.requesterId,
        requesterPhotoUrl = requester?.photoUrl,
        requesterShift = requester?.shift,
        targetName = target?.name ?: swap.targetId,
        targetPhotoUrl = target?.photoUrl,
        targetShift = target?.shift,
        date = date,
        status = swap.status,
        // ShiftSwapCard já envelopa as actions num Row próprio (em
        // linha separada do badge) com spacedBy(8.dp), então só
        // precisamos passar as ações cruas — sem Row extra.
        actions = actions,
    )
}
