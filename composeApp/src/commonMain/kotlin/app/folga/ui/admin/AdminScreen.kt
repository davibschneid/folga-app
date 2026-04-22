package app.folga.ui.admin

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folga.domain.AdminBootstrap
import app.folga.domain.AllowedEmail
import app.folga.domain.User
import app.folga.domain.UserRole
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onBack: () -> Unit,
    viewModel: AdminViewModel = koinViewModel(),
) {
    val users by viewModel.users.collectAsStateWithLifecycle()
    val allowedEmails by viewModel.allowedEmails.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val me by viewModel.currentUser.collectAsStateWithLifecycle()
    val newEmail by viewModel.newEmail.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        snackbar.showSnackbar(current)
        viewModel.clearMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Administração") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(snackbar) { data -> Snackbar { Text(data.visuals.message) } }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Usuários") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("E-mails autorizados") },
                )
            }
            when (selectedTab) {
                0 -> UsersTab(
                    users = users,
                    currentUserId = me?.id,
                    onToggleRole = viewModel::toggleRole,
                )
                else -> EmailsTab(
                    emails = allowedEmails,
                    newEmail = newEmail,
                    onNewEmailChange = viewModel::onNewEmailChange,
                    onAdd = viewModel::addEmail,
                    onRemove = viewModel::removeEmail,
                )
            }
        }
    }
}

@Composable
private fun UsersTab(
    users: List<User>,
    currentUserId: String?,
    onToggleRole: (User) -> Unit,
) {
    if (users.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Nenhum usuário ainda.", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(users, key = { it.id }) { user ->
            UserRow(
                user = user,
                isMe = user.id == currentUserId,
                onToggleRole = { onToggleRole(user) },
            )
        }
    }
}

@Composable
private fun UserRow(
    user: User,
    isMe: Boolean,
    onToggleRole: () -> Unit,
) {
    val isBootstrap = AdminBootstrap.isBootstrapAdmin(user.email)
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (isMe) "${user.name} (você)" else user.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(user.email, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = when (user.role) {
                        UserRole.ADMIN -> if (isBootstrap) "Admin (bootstrap)" else "Admin"
                        UserRole.USER -> "Usuário"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(8.dp))
            // isMe  → bloqueia auto-demoção acidental.
            // isBootstrap + ADMIN → não adianta despromover (o bootstrap
            // volta na próxima autenticação), esconder reduz confusão.
            val lockedByBootstrap = isBootstrap && user.role == UserRole.ADMIN
            OutlinedButton(
                onClick = onToggleRole,
                enabled = !isMe && !lockedByBootstrap,
            ) {
                Text(
                    text = when (user.role) {
                        UserRole.ADMIN -> "Tornar usuário"
                        UserRole.USER -> "Tornar admin"
                    },
                )
            }
        }
    }
}

@Composable
private fun EmailsTab(
    emails: List<AllowedEmail>,
    newEmail: String,
    onNewEmailChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Autorizar novo e-mail",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newEmail,
                    onValueChange = onNewEmailChange,
                    label = { Text("E-mail") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                    Text("Autorizar")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Admins bootstrap (sempre autorizados):",
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(4.dp))
        for (admin in AdminBootstrap.ADMIN_EMAILS) {
            Text("• $admin", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        Text("E-mails autorizados", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (emails.isEmpty()) {
            Text(
                "Nenhum e-mail autorizado além dos admins bootstrap.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(emails, key = { it.email }) { allowed ->
                    AllowedEmailRow(allowed = allowed, onRemove = { onRemove(allowed.email) })
                }
            }
        }
    }
}

@Composable
private fun AllowedEmailRow(
    allowed: AllowedEmail,
    onRemove: () -> Unit,
) {
    val isBootstrap = AdminBootstrap.isBootstrapAdmin(allowed.email)
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(allowed.email, style = MaterialTheme.typography.titleSmall)
                if (allowed.addedBy.isNotBlank()) {
                    Text(
                        "Adicionado por ${allowed.addedBy}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (!isBootstrap) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remover")
                }
            }
        }
    }
}
