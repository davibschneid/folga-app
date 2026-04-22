package app.folga.ui.reports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folga.domain.rules.WorkedDaysReportRow
import app.folga.ui.common.formatBrazilian
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

/**
 * Tela de relatório de dias trabalhados considerando trocas aceitas no
 * período selecionado. ADMIN vê a tabela completa de colaboradores; USER
 * vê só a própria linha. Sem exportação por enquanto — tela só renderiza.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    viewModel: ReportsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rows by viewModel.rows.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Relatório de dias trabalhados") },
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
            Text(
                // Explica o que o relatório mostra pro usuário não
                // confundir com escala. Fica curto — o detalhe vai no
                // docs/regras-de-negocio.md.
                text = "Considera apenas trocas aceitas no período. Cada " +
                    "troca conta -1 dia pra quem cedeu e +1 dia pra quem " +
                    "assumiu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            Text("Período", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    DateField(
                        label = "De",
                        value = state.from,
                        onPick = viewModel::onFromChange,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    DateField(
                        label = "Até",
                        value = state.to,
                        onPick = viewModel::onToChange,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            if (rows.isEmpty()) {
                Text(
                    "Nenhuma troca aceita no período selecionado.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                ReportTable(rows = rows)
            }
        }
    }
}

/**
 * Tabela simples com 4 colunas. Usamos Card + Row em vez de
 * `LazyColumn`/`Row` estruturados porque a base de usuários é pequena
 * (dezenas) e o `verticalScroll` do pai cobre overflow.
 */
@Composable
private fun ReportTable(rows: List<WorkedDaysReportRow>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            HeaderRow()
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            rows.forEachIndexed { idx, row ->
                DataRow(row)
                if (idx != rows.lastIndex) {
                    HorizontalDivider(
                        Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderRow() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Colaborador",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(2f),
        )
        NumberHeader("Cedidos")
        NumberHeader("Assumidos")
        NumberHeader("Saldo")
    }
}

@Composable
private fun RowScope.NumberHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.weight(1f).padding(start = 4.dp),
    )
}

@Composable
private fun DataRow(row: WorkedDaysReportRow) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(2f)) {
            Text(row.user.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                row.user.team,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            row.cededDays.toString(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        Text(
            row.assumedDays.toString(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        Text(
            // Prefixo + pra saldo positivo deixa óbvio que o colaborador
            // trabalhou mais que o esperado. Negativo já vem com o sinal.
            text = if (row.balance > 0) "+${row.balance}" else row.balance.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = when {
                row.balance > 0 -> MaterialTheme.colorScheme.primary
                row.balance < 0 -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
    }
}

/**
 * Campo de data no padrão usado em outras telas (FolgaDatePickerField):
 * OutlinedTextField read-only exibindo DD/MM/AAAA + ícone de calendário
 * que abre DatePickerDialog. Armazena internamente em `LocalDate` e só
 * formata pra display.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    label: String,
    value: LocalDate,
    onPick: (LocalDate) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    val display = remember(value) { value.formatBrazilian() }

    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
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
        val initialEpochMillis = value
            .atStartOfDayIn(TimeZone.UTC)
            .toEpochMilliseconds()
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
                        onPick(picked)
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
