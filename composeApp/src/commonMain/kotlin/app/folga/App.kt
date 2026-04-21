package app.folga

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.folga.domain.AuthRepository
import app.folga.domain.User
import app.folga.ui.completarcadastro.CompletarCadastroScreen
import app.folga.ui.folgas.FolgasScreen
import app.folga.ui.login.LoginScreen
import app.folga.ui.register.RegisterScreen
import app.folga.ui.swap.SwapsScreen
import app.folga.ui.theme.FolgaTheme
import org.koin.compose.KoinContext
import org.koin.compose.koinInject

sealed interface Screen {
    data object Login : Screen
    data object Register : Screen
    data object CompletarCadastro : Screen
    data object Folgas : Screen
    data object Swaps : Screen
}

/**
 * Usuário que entrou com Google mas ainda não terminou de preencher os campos
 * que o Google não fornece (matrícula, equipe, turno). Nessa condição ele é
 * forçado pra CompletarCadastroScreen antes de entrar na parte principal do
 * app — mesmo nas telas de Folgas/Swaps, senão ele poderia abrir o app, ver
 * o fluxo e nunca preencher.
 */
private fun User.isProfileComplete(): Boolean =
    registrationNumber.isNotBlank() && team.isNotBlank()

@Composable
fun App() {
    KoinContext {
        FolgaTheme {
            AppContent()
        }
    }
}

@Composable
private fun AppContent() {
    val authRepository = koinInject<AuthRepository>()
    val currentUser by authRepository.currentUser.collectAsState(initial = null)
    var screen by remember { mutableStateOf<Screen>(Screen.Login) }

    // Auto-navigate based on auth + profile state.
    val user = currentUser
    val loggedIn = user != null
    val profileComplete = user?.isProfileComplete() == true

    if (!loggedIn && screen !is Screen.Login && screen !is Screen.Register) {
        screen = Screen.Login
    }
    if (loggedIn && !profileComplete) {
        // Força o usuário a completar o cadastro antes de entrar no app,
        // independente de onde ele estava (Login/Register/Folgas/Swaps).
        screen = Screen.CompletarCadastro
    }
    if (loggedIn && profileComplete &&
        (screen is Screen.Login || screen is Screen.Register || screen is Screen.CompletarCadastro)
    ) {
        screen = Screen.Folgas
    }

    when (screen) {
        Screen.Login -> LoginScreen(
            onNavigateToRegister = { screen = Screen.Register },
        )

        Screen.Register -> RegisterScreen(
            onBack = { screen = Screen.Login },
        )

        Screen.CompletarCadastro -> CompletarCadastroScreen()

        Screen.Folgas -> FolgasScreen(
            onOpenSwaps = { screen = Screen.Swaps },
        )

        Screen.Swaps -> SwapsScreen(
            onBack = { screen = Screen.Folgas },
        )
    }
}
