package app.folga.ui.swap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folga.domain.Folga
import app.folga.domain.FolgaStatus
import app.folga.domain.Shift
import app.folga.domain.SwapRequest
import app.folga.domain.SwapStatus
import app.folga.domain.User
import app.folga.ui.common.formatBrazilian
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapsScreen(
    onBack: () -> Unit,
    viewModel: SwapsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val my by viewModel.myFolgas.collectAsStateWithLifecycle()
    val colleagues by viewModel.colleagueFolgas.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val incoming by viewModel.incoming.collectAsStateWithLifecycle()
    val outgoing by viewModel.outgoing.collectAsStateWithLifecycle()
    val quota by viewModel.quotaStatus.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trocas de Folga") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Voltar") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            quota?.let { q ->
                // Chip informativo: usuário vê sua quota corrente antes de
                // solicitar uma troca, evitando surpresa no diálogo de aviso.
                AssistChip(
                    onClick = {},
                    label = { Text(quotaChipLabel(q.shift, q.used, q.quota)) },
                    colors = AssistChipDefaults.assistChipColors(),
                )
                Spacer(Modifier.height(8.dp))
            }

            Text("Solicitar troca", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            Text("Minhas folgas agendadas", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            FolgaChips(
                folgas = my.filter { it.status == FolgaStatus.SCHEDULED },
                selectedId = state.selectedMyFolgaId,
                onSelect = viewModel::selectMy,
                users = users,
            )

            Spacer(Modifier.height(12.dp))
            Text("Folgas de colegas", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            FolgaChips(
                folgas = colleagues.filter { it.status == FolgaStatus.SCHEDULED },
                selectedId = state.selectedTargetFolgaId,
                onSelect = viewModel::selectTarget,
                users = users,
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
            Button(
                onClick = viewModel::requestSwap,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
            ) { Text("Solicitar troca") }

            Spacer(Modifier.height(24.dp))
            Text("Recebidas", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (incoming.isEmpty()) Text("Nenhuma solicitação recebida.")
            incoming.forEach { swap ->
                SwapRow(
                    swap = swap,
                    users = users,
                    actions = {
                        if (swap.status == SwapStatus.PENDING) {
                            Button(onClick = { viewModel.accept(swap.id) }) { Text("Aceitar") }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = { viewModel.reject(swap.id) }) { Text("Recusar") }
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(16.dp))
            Text("Enviadas", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (outgoing.isEmpty()) Text("Nenhuma solicitação enviada.")
            outgoing.forEach { swap ->
                SwapRow(
                    swap = swap,
                    users = users,
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

    // Aviso (não-bloqueante) quando o usuário já está no limite de trocas
    // aceitas iniciadas no período (dia 16→15). Confirmar segue com a
    // solicitação normalmente; cancelar mantém o botão no estado anterior.
    state.quotaWarning?.let { warn ->
        AlertDialog(
            onDismissRequest = viewModel::dismissQuotaWarning,
            title = { Text("Quota de trocas atingida") },
            text = {
                Text(
                    "Você já tem ${warn.used} de ${warn.quota} trocas aceitas no período " +
                        "(dia 16 → dia 15) para o turno ${shiftLabel(warn.shift)}. " +
                        "Se essa solicitação for aceita, você vai passar do limite. " +
                        "Deseja continuar mesmo assim?",
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmOverQuota) { Text("Continuar") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissQuotaWarning) { Text("Cancelar") }
            },
        )
    }
}

private fun quotaChipLabel(shift: Shift, used: Int, quota: Int): String =
    "Trocas no período: $used/$quota · ${shiftLabel(shift)}"

private fun shiftLabel(shift: Shift): String = when (shift) {
    Shift.MANHA -> "Manhã"
    Shift.TARDE -> "Tarde"
    Shift.NOITE -> "Noite"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolgaChips(
    folgas: List<Folga>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    users: List<User>,
) {
    if (folgas.isEmpty()) {
        Text("Nenhuma folga disponível.", style = MaterialTheme.typography.bodySmall)
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        folgas.forEach { folga ->
            val owner = users.firstOrNull { it.id == folga.userId }?.name ?: "—"
            FilterChip(
                selected = selectedId == folga.id,
                onClick = { onSelect(folga.id) },
                label = { Text("${folga.date.formatBrazilian()} · $owner") },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

@Composable
private fun SwapRow(
    swap: SwapRequest,
    users: List<User>,
    actions: @Composable () -> Unit,
) {
    val requester = users.firstOrNull { it.id == swap.requesterId }?.name ?: swap.requesterId
    val target = users.firstOrNull { it.id == swap.targetId }?.name ?: swap.targetId
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("$requester ↔ $target", style = MaterialTheme.typography.titleSmall)
            Text("Status: ${swap.status.name}", style = MaterialTheme.typography.bodySmall)
            if (!swap.message.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(swap.message, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { actions() }
        }
    }
}
