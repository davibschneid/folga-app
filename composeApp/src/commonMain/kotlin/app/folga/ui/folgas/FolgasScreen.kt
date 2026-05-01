package app.folga.ui.folgas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folga.domain.FolgaStatus
import app.folga.domain.Holiday
import app.folga.ui.common.*
import kotlinx.datetime.*
import org.koin.compose.viewmodel.koinViewModel

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
    val holidays by viewModel.holidays.collectAsStateWithLifecycle()
    
    // Sempre que a tela inicial (Home) for aberta, limpamos mensagens
    // de sucesso/erro residuais de operações anteriores.
    LaunchedEffect(Unit) {
        viewModel.clearMessages()
    }

    // Filtramos apenas as confirmadas para a seção "Meus dias cadastrados"
    // embora no mock principal apareça apenas "Trocas Agendadas".
    val myScheduled = folgas
        .filter { it.status == FolgaStatus.SCHEDULED }
        .sortedBy { it.date }

    Scaffold(
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
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFFF8FAFC))) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                Box {
                    // Header Azul com curva e saudação agora dentro do scroll
                    HomeHeader(
                        userName = me?.name.orEmpty(),
                        userTeam = me?.team.orEmpty(),
                        userPhotoUrl = me?.photoUrl,
                        pendingSwapsCount = pendingCount,
                        onOpenProfile = onOpenProfile,
                        onOpenNotifications = onOpenSwaps,
                        onOpenReports = onOpenReports
                    )

                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        // Spacer para o card "subir" no azul
                        Spacer(Modifier.height(140.dp))

                        RegistrarDiaCard(
                            date = state.newFolgaDate,
                            note = state.newFolgaNote,
                            error = state.error,
                            success = state.successMessage,
                            isLoading = state.isLoading,
                            onDateChange = viewModel::onDateChange,
                            onNoteChange = viewModel::onNoteChange,
                            onSubmit = viewModel::reserve,
                            onDismissSuccess = viewModel::dismissSuccess,
                            onDismissError = viewModel::dismissError,
                        )
                    }
                }

                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Spacer(Modifier.height(32.dp))
                    Text(
                        text = "Trocas Agendadas",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1F2937)
                        ),
                    )
                    Spacer(Modifier.height(16.dp))

                    if (scheduledSwaps.isEmpty()) {
                        Text(
                            "Nenhuma troca agendada.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
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
                                    viewerRole = if (swap.iAmRequester) SwapViewerRole.REQUESTER else SwapViewerRole.TARGET,
                                )
                            }
                        }
                    }

                    // Se houver dias próprios agendados, mostramos abaixo (opcional, mantendo consistência)
                    if (myScheduled.isNotEmpty()) {
                        Spacer(Modifier.height(32.dp))
                        Text(
                            text = "Meus dias cadastrados",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        )
                        Spacer(Modifier.height(12.dp))
                        myScheduled.forEach { folga ->
                            MyFolgaRow(folga = folga, onCancel = { viewModel.cancel(folga.id) })
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    if (holidays.isNotEmpty()) {
                        Spacer(Modifier.height(32.dp))
                        Text(
                            text = "Próximos Feriados",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1F2937)
                            ),
                        )
                        Spacer(Modifier.height(16.dp))
                        
                        // Pegamos apenas os feriados futuros a partir de hoje
                        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                        val upcomingHolidays = holidays.filter { it.date >= today }.take(5)

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            upcomingHolidays.forEach { holiday ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(holiday.name, fontWeight = FontWeight.SemiBold)
                                            Text(holiday.date.formatBrazilian(), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        }
                                        Surface(
                                            color = Color(0xFFE0F2FE),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                holiday.type,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                color = Color(0xFF0369A1),
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(100.dp)) // Espaço para não ficar atrás da bottom bar
                }
            }
        }
    }
}

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
    onDismissSuccess: () -> Unit,
    onDismissError: () -> Unit,
) {
    Column {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                FolgaDatePickerField(selected = date, onPick = onDateChange)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = onNoteChange,
                    label = { Text("Notas") },
                    placeholder = { Text("Ex: Compensação de banco de horas...") },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.LightGray,
                        unfocusedBorderColor = Color.LightGray
                    )
                )
            }
        }

        if (success != null) {
            Surface(
                modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                color = Color(0xFF0088FF),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Color.White)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = success,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        fontSize = 14.sp
                    )
                    Text(
                        "OK",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onDismissSuccess() }
                    )
                }
            }
        }

        if (error != null) {
            Surface(
                modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                color = MaterialTheme.colorScheme.error,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Color.White) // Mantendo o ícone ou trocando por erro? O CheckCircle em branco fica bom.
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = error,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        fontSize = 14.sp
                    )
                    Text(
                        "OK",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onDismissError() }
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = !isLoading,
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088FF)),
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("REGISTRAR", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolgaDatePickerField(selected: String, onPick: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    val display = remember(selected) {
        runCatching { LocalDate.parse(selected).formatBrazilian() }.getOrNull() ?: ""
    }

    OutlinedTextField(
        value = display,
        onValueChange = {},
        readOnly = true,
        label = { Text("Data") },
        trailingIcon = {
            IconButton(onClick = { showDialog = true }) {
                Icon(Icons.Filled.DateRange, contentDescription = null, tint = Color.Gray)
            }
        },
        modifier = Modifier.fillMaxWidth().clickable { showDialog = true },
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.LightGray,
            unfocusedBorderColor = Color.LightGray
        )
    )

    if (showDialog) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        val picked = Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date
                        onPick(picked.toString())
                    }
                    showDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancelar") }
            }
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun MyFolgaRow(folga: app.folga.domain.Folga, onCancel: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.DateRange, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(folga.date.formatBrazilian(), fontWeight = FontWeight.SemiBold)
                if (!folga.note.isNullOrBlank()) {
                    Text(folga.note, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
            TextButton(onClick = onCancel) {
                Text("Cancelar", color = Color.Red)
            }
        }
    }
}
