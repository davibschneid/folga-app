package app.folga.ui.profile

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folga.ui.common.AppBottomBar
import app.folga.ui.common.MainTab
import app.folga.ui.common.ProfileAvatar
import app.folga.ui.common.ShiftDropdown
import app.folga.ui.common.rememberImagePicker
import org.koin.compose.viewmodel.koinViewModel

/**
 * Tela de Perfil redesenhada:
 * - Header com avatar grande + botão "alterar foto" (abre picker nativo
 *   e faz upload pro Firebase Storage via [ProfileViewModel.pickPhoto]).
 * - Form de edição (nome, matrícula, equipe, turno) — email read-only.
 * - Lista de atalhos: Relatório sempre; Administração só pra ADMIN.
 * - Bottom bar pra voltar pra Home/Trocas sem precisar do botão "Voltar".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onOpenSwaps: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenAdmin: (() -> Unit)? = null,
    onLogout: () -> Unit = {},
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launchPicker = rememberImagePicker(onPicked = viewModel::pickPhoto)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meu perfil") },
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
        bottomBar = {
            AppBottomBar(
                selected = MainTab.PROFILE,
                pendingSwapsCount = state.pendingSwapsCount,
                onSelectHome = onBack,
                onSelectSwaps = onOpenSwaps,
                onSelectProfile = {},
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            AvatarBlock(
                name = state.name,
                photoUrl = state.photoUrl,
                isUploading = state.isUploadingPhoto,
                onChangePhoto = launchPicker,
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = state.email,
                onValueChange = {},
                label = { Text("E-mail") },
                singleLine = true,
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Nome") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.registrationNumber,
                onValueChange = viewModel::onRegistrationNumberChange,
                label = { Text("Matrícula") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.team,
                onValueChange = viewModel::onTeamChange,
                label = { Text("Equipe / Setor") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            ShiftDropdown(
                selected = state.shift,
                onSelect = viewModel::onShiftChange,
            )
            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (state.savedMessage != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = state.savedMessage!!,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSaving,
            ) {
                if (state.isSaving) CircularProgressIndicator() else Text("Salvar")
            }
            Spacer(Modifier.height(24.dp))
            ShortcutsSection(
                onOpenReports = onOpenReports,
                onOpenAdmin = onOpenAdmin,
                // O signOut em si roda no scope estável do App (em
                // App.kt) — disparar via viewModel.signOut() rodava
                // no viewModelScope, que era cancelado quando a tela
                // saía do Composable, e o signOut nunca completava.
                // Aqui só repassamos o callback.
                onLogout = onLogout,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AvatarBlock(
    name: String,
    photoUrl: String?,
    isUploading: Boolean,
    onChangePhoto: () -> Unit,
) {
    Box(contentAlignment = Alignment.BottomEnd) {
        ProfileAvatar(
            name = name,
            photoUrl = photoUrl,
            size = 120.dp,
        )
        // Botão flutuante de câmera ancorado no canto inferior-direito
        // do avatar. Durante o upload vira um spinner — evita cliques
        // múltiplos enquanto os bytes estão sendo enviados pro Storage.
        Box(
            modifier = Modifier
                .size(40.dp)
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isUploading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp,
                )
            } else {
                IconButton(
                    onClick = onChangePhoto,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoCamera,
                        contentDescription = "Alterar foto",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onChangePhoto, enabled = !isUploading) {
        Text(if (photoUrl.isNullOrBlank()) "Adicionar foto" else "Trocar foto")
    }
}

@Composable
private fun ShortcutsSection(
    onOpenReports: () -> Unit,
    onOpenAdmin: (() -> Unit)?,
    onLogout: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            ShortcutRow(
                icon = Icons.Filled.Assessment,
                label = "Relatório de dias trabalhados",
                onClick = onOpenReports,
            )
            if (onOpenAdmin != null) {
                HorizontalDivider()
                ShortcutRow(
                    icon = Icons.Filled.AdminPanelSettings,
                    label = "Administração",
                    onClick = onOpenAdmin,
                )
            }
            HorizontalDivider()
            // Sair: ProfileViewModel.signOut() chama AuthRepository.signOut()
            // que zera authRepository.currentUser. App.kt observa esse fluxo
            // e cai pra tela de Login automaticamente — sem precisar de
            // callback de navegação aqui.
            ShortcutRow(
                icon = Icons.AutoMirrored.Filled.Logout,
                label = "Sair",
                onClick = onLogout,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ShortcutRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.Unspecified,
) {
    val resolvedTint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.primary else tint
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = resolvedTint,
        )
        Spacer(Modifier.size(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = resolvedTint,
        )
    }
}
