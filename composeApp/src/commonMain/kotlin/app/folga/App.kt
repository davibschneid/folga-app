package app.folga

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.folga.domain.AuthRepository
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
    data object Folgas : Screen
    data object Swaps : Screen
}

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

    // Auto-navigate based on auth state
    val loggedIn = currentUser != null
    if (loggedIn && (screen is Screen.Login || screen is Screen.Register)) {
        screen = Screen.Folgas
    }
    if (!loggedIn && (screen is Screen.Folgas || screen is Screen.Swaps)) {
        screen = Screen.Login
    }

    when (screen) {
        Screen.Login -> LoginScreen(
            onNavigateToRegister = { screen = Screen.Register },
        )

        Screen.Register -> RegisterScreen(
            onBack = { screen = Screen.Login },
        )

        Screen.Folgas -> FolgasScreen(
            onOpenSwaps = { screen = Screen.Swaps },
        )

        Screen.Swaps -> SwapsScreen(
            onBack = { screen = Screen.Folgas },
        )
    }
}
