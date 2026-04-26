package app.folga.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folga.auth.GoogleSignInProvider
import app.folga.auth.GoogleSignInResult
import app.folga.domain.AuthRepository
import app.folga.domain.AuthResult
import app.folga.domain.UserRepository
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
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update {
        it.copy(email = value, error = null, infoMessage = null)
    }
    fun onPasswordChange(value: String) = _state.update {
        it.copy(password = value, error = null, infoMessage = null)
    }

    /**
     * Fluxo "Esqueci minha senha": reaproveita o e-mail digitado no
     * campo de Login e dispara o envio do link de redefinição via
     * `FirebaseAuth.sendPasswordResetEmail`.
     *
     * Gate: o e-mail precisa existir no Firestore `users`. Sem esse
     * gate, qualquer um poderia usar o botão como oráculo pra
     * descobrir se determinado endereço tem conta no app (o Firebase
     * só responde com erro pra e-mails que NUNCA logaram, e ainda
     * assim só na chamada — não vaza listagem). Bloquear pelos `users`
     * deixa explícito que o reset é só pra quem já completou cadastro
     * pelo app, não pra qualquer e-mail no Firebase Auth.
     *
     * Reset de senha não cria usuário, então mesmo que o e-mail esteja
     * em [allowed_emails] mas ainda não tenha logado nenhuma vez, ele
     * não vai estar em `users` — comportamento intencional, o usuário
     * precisa concluir o cadastro/primeiro login antes.
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
            // 1) Verifica se o e-mail tem perfil no `users`. Se a leitura
            // falhar (rede/permissão), reportamos erro genérico em vez de
            // disparar o reset cego — assim a gente não mente "enviado"
            // quando na verdade nem checou.
            val existingUser = runCatching { userRepository.findByEmail(email) }
                .getOrElse { ex ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = ex.message ?: "Erro ao verificar e-mail. Tente novamente.",
                        )
                    }
                    return@launch
                }
            if (existingUser == null) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "E-mail não encontrado. Verifique se digitou corretamente ou solicite cadastro ao administrador.",
                    )
                }
                return@launch
            }
            // 2) E-mail existe — pede o link de reset pro Firebase. Só
            // mostramos sucesso se o Firebase aceitar.
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
