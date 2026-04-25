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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folga.domain.Folga
import app.folga.domain.FolgaStatus
import app.folga.ui.common.AppBottomBar
import app.folga.ui.common.HomeHeader
import app.folga.ui.common.MainTab
import app.folga.ui.common.ShiftSwapCard
import app.folga.ui.common.formatBrazilian
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

/**
 * Tela inicial — "Registrar Dia de Trabalho" + lista de trocas agendadas
 * + dias cadastrados que o usuário ainda pode cancelar.
 *
 * O design segue o mock enviado pelo cliente: header azul com
 * saudação/equipe/sino no topo, conteúdo rolável no meio, e barra
 * inferior com abas Home/Trocas/Perfil. A entrada pra tela de Admin
 * mudou pra dentro do Perfil — fora da home pra liberar espaço e
 * porque Admin é ação pouco frequente.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolgasScreen(
    onOpenSwaps: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenReports: () -> Unit,
    viewModel: FolgasViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val me by viewModel.currentUser.collectAsStateWithLifecycle()
    val scheduledSwaps by viewModel.scheduledSwaps.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingIncomingCount.collectAsStateWithLifecycle()
    val folgas by viewModel.folgas.collectAsStateWithLifecycle()
    val myScheduled = folgas.filter { it.status == FolgaStatus.SCHEDULED }

    Scaffold(
        topBar = {
            HomeHeader(
                userName = me?.name.orEmpty(),
                userTeam = me?.team.orEmpty(),
                userPhotoUrl = me?.photoUrl,
                pendingSwapsCount = pendingCount,
                onOpenProfile = onOpenProfile,
                // Sino leva pra tela de Trocas (onde as recebidas ficam
                // listadas). Mais direto pro caso de uso principal do
                // sino: "ver quem me pediu troca".
                onOpenNotifications = onOpenSwaps,
                onOpenReports = onOpenReports,
            )
        },
        bottomBar = {
            AppBottomBar(
                selected = MainTab.HOME,
                pendingSwapsCount = pendingCount,
                onSelectHome = {},
                onSelectSwaps = onOpenSwaps,
                onSelectProfile = onOpenProfile,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(16.dp))
            RegistrarDiaCard(
                date = state.newFolgaDate,
                note = state.newFolgaNote,
                error = state.error,
                success = state.successMessage,
                isLoading = state.isLoading,
                onDateChange = viewModel::onDateChange,
                onNoteChange = viewModel::onNoteChange,
                onSubmit = viewModel::reserve,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Trocas Agendadas",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            if (scheduledSwaps.isEmpty()) {
                Text(
                    "Nenhuma troca agendada.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    scheduledSwaps.forEach { swap ->
                        ShiftSwapCard(
                            requesterName = swap.requesterName,
                            requesterPhotoUrl = swap.requesterPhotoUrl,
                            requesterShift = swap.requesterShift,
                            targetName = swap.targetName,
                            targetPhotoUrl = swap.targetPhotoUrl,
                            targetShift = swap.targetShift,
                            date = swap.date,
                            status = swap.status,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "Meus dias cadastrados",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            if (myScheduled.isEmpty()) {
                Text(
                    "Nenhum dia cadastrado.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    myScheduled.forEach { folga ->
                        MyFolgaRow(folga = folga, onCancel = { viewModel.cancel(folga.id) })
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Card branco de "Registrar Dia de Trabalho" com Data + Notas + botão
 * principal azul (espelha o mock). Mantém as mensagens de erro/sucesso
 * inline logo abaixo do formulário.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegistrarDiaCard(
    date: String,
    note: String,
    error: String?,
    success: String?,
    isLoading: Boolean,
    onDateChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Registrar Dia de Trabalho",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(12.dp))
            FolgaDatePickerField(selected = date, onPick = onDateChange)
            Text(
                text = "DD/MM/AAAA",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                label = { Text("Notas") },
                placeholder = { Text("Adicionar observações sobre o turno...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            if (success != null) {
                Spacer(Modifier.height(8.dp))
                Text(success, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E3A8A),
                    contentColor = Color.White,
                ),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Filled.EventAvailable,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("REGISTRAR")
                }
            }
        }
    }
}

/**
 * Linha dos dias cadastrados pelo próprio usuário com botão Cancelar.
 */
@Composable
private fun MyFolgaRow(folga: Folga, onCancel: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolgaDatePickerField(
    selected: String,
    onPick: (String) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    val display = remember(selected) {
        runCatching { LocalDate.parse(selected).formatBrazilian() }.getOrNull() ?: ""
    }

    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            label = { Text("Data") },
            trailingIcon = {
                IconButton(onClick = { showDialog = true }) {
                    Icon(Icons.Filled.DateRange, contentDescription = "Escolher data")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
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
