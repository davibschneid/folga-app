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
    // Flag pra travar o fluxo de logout: navegamos pra Login na hora
    // do clique pra UX ficar instantânea, mas o `currentUser` só
    // emite null depois que o `auth.signOut()` terminar. Sem essa
    // flag, o bloco de auto-redirect "loggedIn && screen is Login →
    // Folgas" abaixo bumparia o usuário de volta pra Home enquanto
    // o sign-out tá em flight — efeito de "flicker" que parecia o
    // app fechando. A flag é resetada quando `currentUser` cai pra
    // null (signal mais robusto que `try/finally` no launch — cobre
    // o caso de o appScope ser cancelado no meio).
    var isLoggingOut by remember { mutableStateOf(false) }
    // Reports pode ser aberto da Home (atalho no header) ou do Perfil
    // (item da lista). Sem rastrear a origem, o "voltar" sempre cai no
    // mesmo lugar — o que confunde quem entrou pela Home. Guardamos a
    // tela anterior em `reportsOrigin` quando navegamos pra Reports.
    var reportsOrigin by remember { mutableStateOf<Screen>(Screen.Folgas) }

    // Auto-navigate based on auth + profile state.
    val user = currentUser
    val loggedIn = user != null
    val profileComplete = user?.isProfileComplete() == true

    // Reset da flag de logout assim que o auth state confirma que
    // o usuário saiu de fato. Daqui em diante o auto-redirect volta
    // a funcionar normal (login subsequente bumpa Login → Folgas).
    if (!loggedIn && isLoggingOut) {
        isLoggingOut = false
    }

    if (!loggedIn && screen !is Screen.Login && screen !is Screen.Register) {
        screen = Screen.Login
    }
    if (loggedIn && !profileComplete) {
        screen = Screen.CompletarCadastro
    }
    if (loggedIn && profileComplete && !isLoggingOut &&
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
            // Logout: navegamos eager pra Login (UX instantânea) e
            // disparamos o `signOut` async no `appScope`. A flag
            // `isLoggingOut` evita que o bloco de auto-redirect
            // logo acima ("loggedIn && screen is Login → Folgas")
            // bumpasse o usuário de volta pra Home enquanto o
            // `auth.signOut()` ainda não terminou — sem esse guard,
            // o fluxo era: clica Sair → screen=Login → recompose
            // com loggedIn ainda true → bump pra Folgas (em alguns
            // devices isso parecia o app fechando).
            //
            // O reset da flag `isLoggingOut` acontece no recompose
            // que vê `!loggedIn` (linha acima), não aqui — assim
            // cobrimos também o cenário onde o appScope é cancelado
            // por mudança de configuração no meio do signOut.
            onLogout = {
                isLoggingOut = true
                screen = Screen.Login
                appScope.launch {
                    // runCatching aqui é cinto + suspensório: o
                    // `signOut` interno já envolve `clearToken` e
                    // `auth.signOut()` em runCatching. Mas se algo
                    // escapar (ex.: `manualUser.value = null` racing
                    // com observer que crashou), uma exceção solta
                    // dentro do `appScope.launch` cai no
                    // uncaughtExceptionHandler do thread main no
                    // Android — efeito visível pra o usuário: app
                    // fecha sem mensagem. Engolir aqui mantém o app
                    // estável; o fluxo de auth já está garantido
                    // pelo eager-nav pra Login.
                    runCatching { authRepository.signOut() }
                }
            },
        )

        Screen.Reports -> ReportsScreen(onBack = { screen = reportsOrigin })
    }
}
