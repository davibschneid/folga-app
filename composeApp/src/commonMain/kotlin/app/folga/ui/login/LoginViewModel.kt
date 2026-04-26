package app.folga.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folga.auth.GoogleSignInProvider
import app.folga.auth.GoogleSignInResult
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
     * Stub do "Esqueci minha senha" — pedido do cliente entrou no
     * redesign do layout, mas o fluxo completo (input do e-mail +
     * envio via `FirebaseAuth.sendPasswordResetEmail` + tela/dialog
     * de feedback) fica como follow-up. Por enquanto só mostramos
     * uma mensagem orientando o usuário a falar com o admin pra
     * resetar a senha.
     */
    fun onForgotPassword() {
        _state.update {
            it.copy(
                error = null,
                infoMessage = "Solicite ao administrador a redefinição da sua senha por enquanto.",
            )
        }
    }

    fun submit() {
        val current = _state.value
        if (current.email.isBlank() || current.password.isBlank()) {
            _state.update { it.copy(error = "Preencha email e senha") }
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
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
        _state.update { it.copy(isLoading = true, error = null) }
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
