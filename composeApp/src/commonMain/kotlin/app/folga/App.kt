package app.folga

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.folga.domain.AuthRepository
import app.folga.domain.User
import app.folga.domain.UserRole
import app.folga.ui.admin.AdminScreen
import app.folga.ui.completarcadastro.CompletarCadastroScreen
import app.folga.ui.folgas.FolgasScreen
import app.folga.ui.login.LoginScreen
import app.folga.ui.profile.ProfileScreen
import app.folga.ui.register.RegisterScreen
import app.folga.ui.reports.ReportsScreen
import app.folga.ui.swap.SwapsScreen
import app.folga.ui.theme.FolgaTheme
import kotlinx.coroutines.launch
import org.koin.compose.KoinContext
import org.koin.compose.koinInject

sealed interface Screen {
    data object Login : Screen
    data object Register : Screen
    data object CompletarCadastro : Screen
    data object Folgas : Screen
    data object Swaps : Screen
    data object Admin : Screen
    data object Profile : Screen
    data object Reports : Screen
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
    // Scope estável pra logout — o `signOut` é async (limpa fcmToken
    // do doc do user, depois chama FirebaseAuth.signOut). Se ele
    // rodasse no `viewModelScope` do ProfileViewModel, o cancel do
    // viewModel quando a tela sai do Composable abortava o signOut e
    // o usuário ficava "deslogado mas logado" — App.kt nunca via
    // `currentUser == null` e o redirect pra Login não disparava.
    val appScope = rememberCoroutineScope()
    // Reports pode ser aberto da Home (atalho no header) ou do Perfil
    // (item da lista). Sem rastrear a origem, o "voltar" sempre cai no
    // mesmo lugar — o que confunde quem entrou pela Home. Guardamos a
    // tela anterior em `reportsOrigin` quando navegamos pra Reports.
    var reportsOrigin by remember { mutableStateOf<Screen>(Screen.Folgas) }

    // Auto-navigate based on auth + profile state.
    val user = currentUser
    val loggedIn = user != null
    val profileComplete = user?.isProfileComplete() == true

    if (!loggedIn && screen !is Screen.Login && screen !is Screen.Register) {
        screen = Screen.Login
    }
    if (loggedIn && !profileComplete) {
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

        // Home: com o redesign, a Home usa header azul + bottom bar
        // (Home/Trocas/Perfil). Admin saiu da TopAppBar e virou item
        // dentro do Perfil (ação pouco frequente). Relatório voltou
        // pro header — o cliente pediu pra ficar acessível direto da
        // Home e continua também dentro do Perfil.
        Screen.Folgas -> FolgasScreen(
            onOpenSwaps = { screen = Screen.Swaps },
            onOpenProfile = { screen = Screen.Profile },
            onOpenReports = {
                reportsOrigin = Screen.Folgas
                screen = Screen.Reports
            },
        )

        Screen.Swaps -> SwapsScreen(
            onBack = { screen = Screen.Folgas },
            onOpenProfile = { screen = Screen.Profile },
        )

        Screen.Admin -> {
            // Se o usuário perdeu role de admin enquanto estava aqui,
            // devolve pra Home.
            if (user?.role != UserRole.ADMIN) {
                screen = Screen.Folgas
            } else {
                AdminScreen(onBack = { screen = Screen.Profile })
            }
        }

        Screen.Profile -> ProfileScreen(
            onBack = { screen = Screen.Folgas },
            onOpenSwaps = { screen = Screen.Swaps },
            onOpenReports = {
                reportsOrigin = Screen.Profile
                screen = Screen.Reports
            },
            onOpenAdmin = { screen = Screen.Admin }
                .takeIf { user?.role == UserRole.ADMIN },
            // Logout: dispara o signOut no `appScope` (escopo do App,
            // não do ViewModel). NÃO setamos `screen = Login` aqui:
            // a auto-redirect logic (`!loggedIn && screen !is Login`)
            // pega isso assim que o `currentUser` vira null.
            //
            // Por que não setar o screen direto? Se navegássemos pra
            // Login enquanto `currentUser` ainda fosse não-null (o
            // signOut ainda estava em flight), o bloco
            // `loggedIn && profileComplete && screen is Login`
            // logo abaixo bumparia de volta pra Folgas — o usuário
            // via um "flicker" pra Folgas e em alguns devices a UI
            // parecia travar nesse estado.
            onLogout = {
                appScope.launch { authRepository.signOut() }
            },
        )

        Screen.Reports -> ReportsScreen(onBack = { screen = reportsOrigin })
    }
}
