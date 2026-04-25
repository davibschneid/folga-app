package app.folga.ui.reports

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folga.domain.rules.WorkedDaysReportRow
import app.folga.ui.common.ProfileAvatar
import app.folga.ui.common.formatBrazilian
import app.folga.ui.common.rememberSharer
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

/**
 * Tela de relatório de dias trabalhados considerando trocas aceitas no
 * período selecionado. ADMIN vê a tabela completa de colaboradores; USER
 * vê só a própria linha.
 *
 * Layout (mock do cliente):
 * - Header azul com seta de voltar.
 * - Linha "De" / "Até" como cards lado a lado.
 * - Card "Visão Geral dos Dias" com tabela: avatar + nome | Cedidos
 *   | Assumidos | Saldo (verde p/ +, vermelho p/ -).
 * - Rodapé do card: "Exibindo N de N colaboradores" + "Total Cedidos: X
 *   | Total Assumidos: Y".
 * - FAB azul "Compartilhar" no canto inferior direito — abre o share
 *   sheet nativo com o relatório formatado em texto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    viewModel: ReportsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val share = rememberSharer()

    val totalCeded = rows.sumOf { it.cededDays }
    val totalAssumed = rows.sumOf { it.assumedDays }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Relatório de dias trabalhados") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E3A8A),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
        floatingActionButton = {
            // FAB de compartilhar: gera um texto plano com o cabeçalho
            // do período + tabela + totais e abre o share sheet do
            // sistema. O "+" do mock virou ícone de share porque é o
            // caso de uso real (não tem nada pra "adicionar" aqui).
            FloatingActionButton(
                onClick = {
                    share(
                        "Relatório de dias trabalhados",
                        formatReportText(
                            from = state.from,
                            to = state.to,
                            rows = rows,
                            totalCeded = totalCeded,
                            totalAssumed = totalAssumed,
                        ),
                    )
                },
                containerColor = Color(0xFF1E3A8A),
                contentColor = Color.White,
            ) {
                Icon(Icons.Filled.Share, contentDescription = "Compartilhar")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
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

            Spacer(Modifier.height(12.dp))

            Text(
                // Explica o que o relatório mostra pro usuário não
                // confundir com escala. Detalhe completo no
                // docs/regras-de-negocio.md.
                text = "Considera apenas trocas aceitas no período. Cada " +
                    "troca conta -1 dia pra quem cedeu e +1 dia pra quem " +
                    "assumiu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Visão Geral dos Dias",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(12.dp))

                    if (rows.isEmpty()) {
                        Text(
                            "Nenhuma troca aceita no período selecionado.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        HeaderRow()
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        rows.forEachIndexed { idx, row ->
                            DataRow(row)
                            if (idx != rows.lastIndex) {
                                HorizontalDivider(
                                    Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        // Rodapé do mock: "Exibindo N de N colaboradores"
                        // + totais agregados. Como ainda não tem
                        // paginação, visíveis = total. Quando entrar
                        // paginação ajustar pra "visíveis de total".
                        Text(
                            text = "Exibindo ${rows.size} de ${rows.size} colaboradores",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Total Cedidos: $totalCeded | Total Assumidos: $totalAssumed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // Espaço extra pro FAB não cobrir o último item.
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun HeaderRow() {
    // Pesos das colunas: "Assumidos" precisa de mais espaço que as outras
    // numéricas porque é a label mais longa (9 chars). Usar `labelSmall`
    // também ajuda a caber em telas estreitas sem quebrar a palavra.
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Colaborador",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(2.0f),
            maxLines = 1,
        )
        NumberHeader("Cedidos", weight = 1.0f)
        NumberHeader("Assumidos", weight = 1.3f)
        NumberHeader("Saldo", weight = 0.9f)
    }
}

@Composable
private fun RowScope.NumberHeader(text: String, weight: Float) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(weight).padding(start = 4.dp),
        maxLines = 1,
    )
}

@Composable
private fun DataRow(row: WorkedDaysReportRow) {
    // Pesos espelham os do HeaderRow pra colunas alinharem.
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(2.0f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileAvatar(
                name = row.user.name,
                photoUrl = row.user.photoUrl,
                size = 32.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = row.user.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
        }
        Text(
            row.cededDays.toString(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1.0f).padding(start = 4.dp),
        )
        Text(
            row.assumedDays.toString(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1.3f).padding(start = 4.dp),
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
            modifier = Modifier.weight(0.9f).padding(start = 4.dp),
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

/**
 * Formata o relatório como texto plano pro share sheet. Mantém colunas
 * alinhadas com `padEnd`/`padStart` pra ficar legível em apps que
 * preservam fonte monoespaçada (WhatsApp, Notes). Em apps que perdem
 * mono o texto ainda é tabular o suficiente pra leitura.
 */
internal fun formatReportText(
    from: LocalDate,
    to: LocalDate,
    rows: List<WorkedDaysReportRow>,
    totalCeded: Int,
    totalAssumed: Int,
): String = buildString {
    appendLine("Relatório de dias trabalhados")
    appendLine("${from.formatBrazilian()} a ${to.formatBrazilian()}")
    appendLine()
    if (rows.isEmpty()) {
        appendLine("Nenhuma troca aceita no período selecionado.")
        return@buildString
    }
    appendLine(
        listOf(
            "Colaborador".padEnd(24),
            "Cedidos".padStart(8),
            "Assumidos".padStart(10),
            "Saldo".padStart(7),
        ).joinToString(" "),
    )
    rows.forEach { r ->
        val saldo = if (r.balance > 0) "+${r.balance}" else r.balance.toString()
        appendLine(
            listOf(
                r.user.name.take(24).padEnd(24),
                r.cededDays.toString().padStart(8),
                r.assumedDays.toString().padStart(10),
                saldo.padStart(7),
            ).joinToString(" "),
        )
    }
    appendLine()
    appendLine("Total Cedidos: $totalCeded")
    appendLine("Total Assumidos: $totalAssumed")
}
