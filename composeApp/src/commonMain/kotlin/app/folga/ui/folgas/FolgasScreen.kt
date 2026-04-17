package app.folga.ui.folgas

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folga.domain.Folga
import app.folga.domain.FolgaStatus
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolgasScreen(
    onOpenSwaps: () -> Unit,
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
                    OutlinedTextField(
                        value = state.newFolgaDate,
                        onValueChange = viewModel::onDateChange,
                        label = { Text("Data (AAAA-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
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
                Text(folga.date.toString(), style = MaterialTheme.typography.titleMedium)
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
