package app.folga.ui.folgas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folga.domain.Folga
import app.folga.domain.FolgaStatus
import app.folga.domain.SwapStatus
import app.folga.ui.common.formatBrazilian
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolgasScreen(
    onOpenSwaps: () -> Unit,
    onOpenProfile: () -> Unit,
    // Nullo quando o usuário logado não é admin — a TopAppBar esconde o
    // botão de Admin nesse caso. Manter null (ao invés de um lambda vazio)
    // garante que a UI não mostre um botão "morto".
    onOpenAdmin: (() -> Unit)? = null,
    onOpenReports: () -> Unit,
    viewModel: FolgasViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val me by viewModel.currentUser.collectAsStateWithLifecycle()
    val scheduledSwaps by viewModel.scheduledSwaps.collectAsStateWithLifecycle()
    val folgas by viewModel.folgas.collectAsStateWithLifecycle()
    // Só mostra os dias que o usuário ainda pode cancelar. CANCELLED e
    // SWAPPED não fazem sentido listar aqui (já saíram do calendário de
    // compromissos do usuário) e também não teriam botão de ação.
    val myScheduled = folgas.filter { it.status == FolgaStatus.SCHEDULED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meus dias de trabalho") },
                // Com 4 ações possíveis (Admin, Perfil, Relatório, Trocas),
                // usar OutlinedButton com texto estourava a largura da
                // TopAppBar em telas Android típicas (~360dp). Troquei pra
                // IconButton com ícone + contentDescription pra acessibilidade.
                actions = {
                    if (onOpenAdmin != null) {
                        IconButton(onClick = onOpenAdmin) {
                            Icon(
                                Icons.Filled.AdminPanelSettings,
                                contentDescription = "Admin",
                            )
                        }
                    }
                    IconButton(onClick = onOpenReports) {
                        Icon(
                            Icons.Filled.Assessment,
                            contentDescription = "Relatório",
                        )
                    }
                    IconButton(onClick = onOpenSwaps) {
                        Icon(
                            Icons.Filled.SwapHoriz,
                            contentDescription = "Trocas",
                        )
                    }
                    IconButton(onClick = onOpenProfile) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = "Perfil",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            // `verticalScroll` envolve tudo pra permitir duas listas
            // (trocas agendadas + dias cadastrados) sem conflito entre
            // dois LazyColumns aninhados — que quebraria com
            // "infinite height constraints". Folga list tende a ser
            // pequena (< dezenas), virtualização não é crítica.
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = me?.let { "Olá, ${it.name} — ${it.team}" } ?: "Sem usuário",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Cadastrar dia de trabalho", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    FolgaDatePickerField(
                        selected = state.newFolgaDate,
                        onPick = viewModel::onDateChange,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.newFolgaNote,
                        onValueChange = viewModel::onNoteChange,
                        label = { Text("Observação (opcional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = state.error!!,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (state.successMessage != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = state.successMessage!!,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = viewModel::reserve,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading,
                    ) {
                        if (state.isLoading) CircularProgressIndicator() else Text("Cadastrar")
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Trocas agendadas", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (scheduledSwaps.isEmpty()) {
                Text(
                    "Nenhuma troca agendada.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    scheduledSwaps.forEach { swap ->
                        ScheduledSwapRow(swap = swap)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Meus dias cadastrados", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (myScheduled.isEmpty()) {
                Text(
                    "Nenhum dia cadastrado.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    myScheduled.forEach { folga ->
                        MyFolgaRow(folga = folga, onCancel = { viewModel.cancel(folga.id) })
                    }
                }
            }
        }
    }
}

/**
 * Linha exibindo uma troca agendada unidirecional: o [requesterName]
 * cadastrou a [date] e o [targetName] vai trabalhar por ele nesse dia.
 * No modelo unidirecional só existe uma data (o dia sendo transferido).
 */
@Composable
private fun ScheduledSwapRow(swap: ScheduledSwap) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Text(
                text = "${swap.requesterName} → ${swap.targetName}",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            val day = swap.date?.formatBrazilian() ?: "—"
            Text(
                text = "${swap.targetName} trabalha em $day no lugar de ${swap.requesterName}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = statusLabel(swap.status),
                style = MaterialTheme.typography.bodySmall,
                color = when (swap.status) {
                    SwapStatus.ACCEPTED -> MaterialTheme.colorScheme.primary
                    SwapStatus.PENDING -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/**
 * Linha exibindo um dia cadastrado pelo próprio usuário, com botão
 * Cancelar. Só aparece para `FolgaStatus.SCHEDULED` — a filtragem é
 * feita pela tela antes de renderizar.
 */
@Composable
private fun MyFolgaRow(folga: Folga, onCancel: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = folga.date.formatBrazilian(),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (!folga.note.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = folga.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = onCancel) { Text("Cancelar") }
        }
    }
}

private fun statusLabel(status: SwapStatus): String = when (status) {
    SwapStatus.PENDING -> "Aguardando resposta"
    SwapStatus.ACCEPTED -> "Confirmada"
    SwapStatus.REJECTED -> "Recusada"
    SwapStatus.CANCELLED -> "Cancelada"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolgaDatePickerField(
    selected: String,
    onPick: (String) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    val display = remember(selected) {
        // `selected` is stored in ISO (AAAA-MM-DD) by the ViewModel so the
        // repository layer stays locale-agnostic; we only format on display.
        runCatching { LocalDate.parse(selected).formatBrazilian() }.getOrNull() ?: ""
    }

    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            label = { Text("Data (DD/MM/AAAA)") },
            trailingIcon = {
                IconButton(onClick = { showDialog = true }) {
                    Icon(Icons.Filled.DateRange, contentDescription = "Escolher data")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        // Transparent overlay so tapping anywhere on the field opens the
        // picker — readOnly fields don't forward the click on their own.
        Box(
            Modifier
                .matchParentSize()
                .clickable { showDialog = true },
        )
    }

    if (showDialog) {
        val initialEpochMillis = runCatching { LocalDate.parse(selected) }
            .getOrNull()
            ?.atStartOfDayIn(TimeZone.UTC)
            ?.toEpochMilliseconds()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialEpochMillis)
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        val picked = Instant.fromEpochMilliseconds(millis)
                            .toLocalDateTime(TimeZone.UTC)
                            .date
                        onPick(picked.toString())
                    }
                    showDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancelar") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
