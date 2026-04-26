package app.folga

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    // Reports pode ser aberto da Home (atalho no header) ou do Perfil
    // (item da lista). Sem rastrear a origem, o "voltar" sempre cai no
    // mesmo lugar — o que confunde quem entrou pela Home. Guardamos a
    // tela anterior em `reportsOrigin` quando navegamos pra Reports.
    var reportsOrigin by remember { mutableStateOf<Screen>(Screen.Folgas) }
    // Flag que sinaliza "o usuário pediu logout, estamos esperando o
    // signOut() assíncrono propagar pro currentUser". Sem isto, o
    // guard `loggedIn && profileComplete && screen is Login → Folgas`
    // corre antes do `currentUser` virar null e devolve a UI pra Home,
    // o que aparece na tela como flash Home → Login (race condition
    // apontada pelo Devin Review no PR #46).
    var loggingOut by remember { mutableStateOf(false) }

    // Auto-navigate based on auth + profile state.
    val user = currentUser
    val loggedIn = user != null
    val profileComplete = user?.isProfileComplete() == true

    // Quando o signOut finalmente propaga (currentUser=null), zeramos
    // a flag pra que logins futuros caiam de volta no fluxo normal de
    // navegação.
    if (!loggedIn && loggingOut) {
        loggingOut = false
    }

    if (!loggedIn && screen !is Screen.Login && screen !is Screen.Register) {
        screen = Screen.Login
    }
    if (loggedIn && !profileComplete && !loggingOut) {
        screen = Screen.CompletarCadastro
    }
    if (loggedIn && profileComplete && !loggingOut &&
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
            // Após "Sair":
            //   1. Marca `loggingOut = true` pra suprimir o guard que
            //      forçaria voltar pra Home enquanto o `signOut()`
            //      assíncrono ainda não propagou `currentUser=null`.
            //   2. Empurra `screen = Login` na hora pra evitar flash da
            //      Home/Profile durante o intervalo até o currentUser
            //      virar null. Quando o sign out completa (Firebase +
            //      clearToken), o efeito de cima zera `loggingOut` e o
            //      app fica em Login normal.
            onLogout = {
                loggingOut = true
                screen = Screen.Login
            },
        )

        Screen.Reports -> ReportsScreen(onBack = { screen = reportsOrigin })
    }
}
