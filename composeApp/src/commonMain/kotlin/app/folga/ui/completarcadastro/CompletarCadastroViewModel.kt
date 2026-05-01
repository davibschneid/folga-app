package app.folga.ui.completarcadastro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folga.domain.AuthRepository
import app.folga.domain.AuthResult
import app.folga.domain.Shift
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CompletarCadastroUiState(
    val registrationNumber: String = "",
    val team: String = "",
    val shift: Shift = Shift.MANHA,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
)

/**
 * Fluxo de "completar cadastro" pós Google Sign-In. Google nos dá nome e
 * email, mas não tem matrícula/equipe/turno — então criamos um perfil
 * mínimo no Firestore (ver [AuthRepository.signInWithGoogleIdToken]) e o
 * App.kt redireciona pra cá quando detecta que o perfil ainda não tem esses
 * campos preenchidos. Ao salvar, chamamos [AuthRepository.completeProfile]
 * que atualiza o doc no Firestore.
 */
class CompletarCadastroViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CompletarCadastroUiState())
    val state: StateFlow<CompletarCadastroUiState> = _state.asStateFlow()

    fun onRegistrationNumberChange(v: String) =
        _state.update { it.copy(registrationNumber = v, error = null, successMessage = null) }

    fun onTeamChange(v: String) = _state.update { it.copy(team = v, error = null, successMessage = null) }

    fun onShiftChange(v: Shift) = _state.update { it.copy(shift = v, error = null, successMessage = null) }

    fun dismissError() = _state.update { it.copy(error = null) }
    fun dismissSuccess() = _state.update { it.copy(successMessage = null) }

    fun clearMessages() {
        _state.update { it.copy(error = null, successMessage = null) }
    }

    fun submit() {
        val c = _state.value
        if (c.registrationNumber.isBlank() || c.team.isBlank()) {
            _state.update { it.copy(error = "Preencha matrícula e equipe", successMessage = null) }
            return
        }
        _state.update { it.copy(isLoading = true, error = null, successMessage = null) }
        viewModelScope.launch {
            val result = authRepository.completeProfile(
                registrationNumber = c.registrationNumber.trim(),
                team = c.team.trim(),
                shift = c.shift,
            )
            if (result is AuthResult.Failure) {
                _state.update { it.copy(isLoading = false, error = result.message) }
            } else {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }
}
