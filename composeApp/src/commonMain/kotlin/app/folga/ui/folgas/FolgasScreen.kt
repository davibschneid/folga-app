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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
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
    viewModel: FolgasViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val me by viewModel.currentUser.collectAsStateWithLifecycle()
    val folgas by viewModel.folgas.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Minhas Folgas") },
                actions = {
                    if (onOpenAdmin != null) {
                        OutlinedButton(
                            onClick = onOpenAdmin,
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text("Admin")
                        }
                    }
                    OutlinedButton(
                        onClick = onOpenProfile,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text("Perfil")
                    }
                    OutlinedButton(onClick = onOpenSwaps, modifier = Modifier.padding(end = 12.dp)) {
                        Text("Trocas")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
        ) {
            Text(
                text = me?.let { "Olá, ${it.name} — ${it.team}" } ?: "Sem usuário",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Reservar nova folga", style = MaterialTheme.typography.titleSmall)
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
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = viewModel::reserve,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading,
                    ) {
                        if (state.isLoading) CircularProgressIndicator() else Text("Reservar")
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Folgas", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (folgas.isEmpty()) {
                Text("Nenhuma folga reservada ainda.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(folgas, key = { it.id }) { folga ->
                        FolgaRow(folga = folga, onCancel = { viewModel.cancel(folga.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FolgaRow(folga: Folga, onCancel: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(folga.date.formatBrazilian(), style = MaterialTheme.typography.titleMedium)
                Text(folga.status.name, style = MaterialTheme.typography.bodySmall)
                if (!folga.note.isNullOrBlank()) {
                    Text(folga.note, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.width(8.dp))
            if (folga.status == FolgaStatus.SCHEDULED) {
                OutlinedButton(onClick = onCancel) { Text("Cancelar") }
            }
        }
    }
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
