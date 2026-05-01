package app.folga.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folga.auth.GoogleSignInProvider
import app.folga.auth.GoogleSignInResult
import app.folga.domain.AllowedEmailRepository
import app.folga.domain.AuthRepository
import app.folga.domain.AuthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    /**
     * Mensagem informativa (não-erro) exibida abaixo dos campos —
     * usado pelo link "Esqueci minha senha" enquanto o fluxo de reset
     * por e-mail não está implementado. Mantido separado de `error`
     * pra UI poder colorir cada um do jeito certo (azul royal pra info,
     * vermelho pra erro).
     */
    val infoMessage: String? = null,
)

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val googleSignInProvider: GoogleSignInProvider,
    private val allowedEmailRepository: AllowedEmailRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        // Bug encontrado em produção: LoginViewModel sobrevive entre
        // logins (ViewModelStore tied à Activity, não ao login).
        // Eric logava no mesmo aparelho que o Davi tinha logado e
        // saído, depois o Eric saía e o form de Login voltava com
        // o e-mail/senha do Eric ainda preenchidos. Reset state
        // toda vez que detectamos a transição "logado → deslogado".
        // O reset não dispara no boot (currentUser começa null sem
        // ter sido != null antes), então não atrapalha o usuário
        // que está no meio de digitar.
        viewModelScope.launch {
            var wasLoggedIn = false
            authRepository.currentUser.collect { user ->
                val nowLoggedIn = user != null
                if (wasLoggedIn && !nowLoggedIn) {
                    _state.value = LoginUiState()
                }
                wasLoggedIn = nowLoggedIn
            }
        }
    }

    fun onEmailChange(value: String) = _state.update {
        it.copy(email = value, error = null, infoMessage = null)
    }
    fun onPasswordChange(value: String) = _state.update {
        it.copy(password = value, error = null, infoMessage = null)
    }

    fun dismissError() = _state.update { it.copy(error = null) }
    fun dismissInfo() = _state.update { it.copy(infoMessage = null) }

    /**
     * Fluxo "Esqueci minha senha": reaproveita o e-mail digitado no
     * campo de Login e dispara o envio do link de redefinição via
     * `FirebaseAuth.sendPasswordResetEmail`.
     *
     * Gate: o e-mail precisa estar em [allowed_emails] no Firestore.
     * Originalmente o gate era em `users`, mas a rule do `users`
     * exige `isSignedIn()` e nesse fluxo o usuário está deslogado —
     * a query falhava sempre com PERMISSION_DENIED. As rules de
     * `allowed_emails` já permitem `get` público (mesma regra usada
     * pelo gate de cadastro), então a checagem funciona sem auth.
     *
     * Comportamento prático equivalente:
     * - Se o e-mail está autorizado mas ainda não tem conta no
     *   Firebase Auth, o `sendPasswordResetEmail` retorna erro
     *   ("Usuário não encontrado") via `humanMessage()`.
     * - Se está autorizado e tem conta, o e-mail é enviado.
     * - Se não está autorizado, paramos antes mesmo de bater no
     *   Firebase Auth (zero gasto de quota e mensagem clara pro
     *   usuário pedir liberação ao admin).
     */
    fun onForgotPassword() {
        val email = _state.value.email.trim()
        if (email.isBlank()) {
            _state.update {
                it.copy(
                    error = "Digite seu e-mail no campo acima e toque novamente em \"Esqueci minha senha\".",
                    infoMessage = null,
                )
            }
            return
        }
        _state.update { it.copy(isLoading = true, error = null, infoMessage = null) }
        viewModelScope.launch {
            // 0) Reativa a rede do Firestore: o `signOut` desabilita
            // pra evitar PERMISSION_DENIED nos listeners. Esse fluxo
            // roda deslogado (usuário acabou de sair OU nunca logou),
            // então precisa garantir que a leitura abaixo não vai
            // bater no cache offline. Idempotente — no-op se já está
            // online (caso primeiro acesso pós-install).
            authRepository.ensureFirestoreReady()
            // 1) Verifica se o e-mail está autorizado. Se a leitura
            // falhar (rede/permissão), reportamos erro em vez de
            // disparar o reset cego — assim a gente não mente "enviado"
            // quando na verdade nem checou. AllowedEmailRepository tem
            // `get` público nas rules pra esse fluxo funcionar deslogado.
            val isAllowed = runCatching { allowedEmailRepository.isAllowed(email) }
                .getOrElse { ex ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = ex.message ?: "Erro ao verificar e-mail. Tente novamente.",
                        )
                    }
                    return@launch
                }
            if (!isAllowed) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "E-mail não autorizado. Verifique se digitou corretamente ou solicite cadastro ao administrador.",
                    )
                }
                return@launch
            }
            // 2) Autorizado — pede o link de reset pro Firebase. Só
            // mostramos sucesso se o Firebase aceitar (se o e-mail
            // está autorizado mas ainda não tem conta de fato no
            // Auth, cai como Failure com mensagem "Usuário não
            // encontrado", que é a indicação certa pro usuário).
            when (val result = authRepository.sendPasswordResetEmail(email)) {
                is AuthResult.Success -> _state.update {
                    it.copy(
                        isLoading = false,
                        infoMessage = "Enviamos um e-mail com o link de redefinição. Confira sua caixa de entrada.",
                    )
                }
                is AuthResult.Failure -> _state.update {
                    it.copy(isLoading = false, error = result.message)
                }
            }
        }
    }

    fun submit() {
        val current = _state.value
        if (current.email.isBlank() || current.password.isBlank()) {
            _state.update { it.copy(error = "Preencha email e senha", infoMessage = null) }
            return
        }
        _state.update { it.copy(isLoading = true, error = null, infoMessage = null) }
        viewModelScope.launch {
            val result = authRepository.signInWithEmail(current.email.trim(), current.password)
            if (result is AuthResult.Failure) {
                _state.update { it.copy(isLoading = false, error = result.message) }
            } else {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * Triggers the native Google Sign-In flow. On Android this shows the
     * Credential Manager bottom sheet; on iOS it opens the GoogleSignIn SDK
     * flow. The resulting ID token is exchanged for a Firebase credential by
     * [AuthRepository.signInWithGoogleIdToken].
     */
    fun signInWithGoogle() {
        _state.update { it.copy(isLoading = true, error = null, infoMessage = null) }
        viewModelScope.launch {
            when (val google = googleSignInProvider.signIn()) {
                is GoogleSignInResult.Cancelled -> {
                    // User backed out — clear the spinner silently, don't show
                    // an error (dismissing the sheet is a valid choice).
                    _state.update { it.copy(isLoading = false) }
                }
                is GoogleSignInResult.Failure -> {
                    _state.update { it.copy(isLoading = false, error = google.message) }
                }
                is GoogleSignInResult.Success -> {
                    val result = authRepository.signInWithGoogleIdToken(
                        idToken = google.idToken,
                        email = google.email,
                        name = google.displayName,
                    )
                    if (result is AuthResult.Failure) {
                        _state.update { it.copy(isLoading = false, error = result.message) }
                    } else {
                        _state.update { it.copy(isLoading = false) }
                    }
                }
            }
        }
    }
}
