package app.folga.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.folga.domain.Shift

/**
 * Labels em português do Brasil pros turnos. Mantido no common porque
 * Compose Multiplatform não tem resource loader pro `strings.xml` em iOS.
 */
fun Shift.label(): String = when (this) {
    Shift.MANHA -> "Manhã"
    Shift.TARDE -> "Tarde"
    Shift.NOITE -> "Noite"
}

/**
 * Combobox (Material3 ExposedDropdownMenu) para escolher o turno do
 * colaborador. Usado na tela de Cadastro (email/senha) e na tela de
 * Completar cadastro (pós login com Google). Isola o layout aqui pra não
 * duplicar o boilerplate de `ExposedDropdownMenuBox` nos dois lugares.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftDropdown(
    selected: Shift,
    onSelect: (Shift) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Turno",
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected.label(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            Shift.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label()) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
