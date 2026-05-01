package app.folga.ui.register

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

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val registrationNumber: String = "",
    val team: String = "",
    val shift: Shift = Shift.MANHA,
    val isLoading: Boolean = false,
    val error: String? = null,
)

class RegisterViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun onNameChange(v: String) = _state.update { it.copy(name = v, error = null) }
    fun onEmailChange(v: String) = _state.update { it.copy(email = v, error = null) }
    fun onPasswordChange(v: String) = _state.update { it.copy(password = v, error = null) }
    fun onRegistrationNumberChange(v: String) = _state.update { it.copy(registrationNumber = v, error = null) }
    fun onTeamChange(v: String) = _state.update { it.copy(team = v, error = null) }
    fun onShiftChange(v: Shift) = _state.update { it.copy(shift = v, error = null) }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun submit() {
        val c = _state.value
        if (c.name.isBlank() || c.email.isBlank() || c.password.length < 6 ||
            c.registrationNumber.isBlank() || c.team.isBlank()
        ) {
            _state.update { it.copy(error = "Preencha todos os campos (senha ≥ 6 caracteres)") }
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = authRepository.signUpWithEmail(
                email = c.email.trim(),
                password = c.password,
                name = c.name.trim(),
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
}
