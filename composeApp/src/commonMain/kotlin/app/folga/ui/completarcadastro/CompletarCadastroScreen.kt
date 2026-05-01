package app.folga.ui.completarcadastro

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folga.ui.common.ShiftDropdown
import org.koin.compose.viewmodel.koinViewModel

/**
 * Pós Google Sign-In o usuário cai aqui se matrícula/equipe ainda não
 * foram preenchidas. Email/nome já vêm do Google e não podem ser
 * alterados nessa tela — a edição de perfil fica pra uma tela futura.
 */
@Composable
fun CompletarCadastroScreen(
    viewModel: CompletarCadastroViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Completar cadastro", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Precisamos de mais algumas informações pra concluir seu cadastro.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = state.registrationNumber,
            onValueChange = viewModel::onRegistrationNumberChange,
            label = { Text("Matrícula") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.team,
            onValueChange = viewModel::onTeamChange,
            label = { Text("Equipe / Setor") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        ShiftDropdown(
            selected = state.shift,
            onSelect = viewModel::onShiftChange,
        )

        if (state.successMessage != null) {
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
                        text = state.successMessage!!,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        fontSize = 14.sp
                    )
                    Text(
                        "OK",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { viewModel.dismissSuccess() }
                    )
                }
            }
        }

        if (state.error != null) {
            Surface(
                modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                color = MaterialTheme.colorScheme.error,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Color.White)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = state.error!!,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        fontSize = 14.sp
                    )
                    Text(
                        "OK",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { viewModel.dismissError() }
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = viewModel::submit,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading,
        ) {
            if (state.isLoading) CircularProgressIndicator() else Text("Salvar")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = viewModel::signOut,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading,
        ) {
            Text("Sair")
        }
    }
}
