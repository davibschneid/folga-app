package app.folga.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folga.domain.AuthRepository
import app.folga.domain.AuthResult
import app.folga.domain.Shift
import app.folga.domain.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val email: String = "",
    val name: String = "",
    val registrationNumber: String = "",
    val team: String = "",
    val shift: Shift = Shift.MANHA,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedMessage: String? = null,
)

/**
 * ViewModel da tela de edição de perfil. Carrega o usuário atual do
 * [AuthRepository.currentUser] pré-preenchendo os campos editáveis
 * (nome, matrícula, equipe, turno). E-mail fica read-only. Ao salvar,
 * chama [AuthRepository.updateProfile] — a mesma emissão em
 * `manualUser` atualiza o resto do app (incluindo o nome na
 * `FolgasScreen`).
 */
class ProfileViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState(isLoading = true))
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Pega o primeiro valor não-nulo do currentUser pra preencher os
            // campos. `first { it != null }` — se o usuário não tá logado,
            // a tela nem deveria estar acessível (ver App.kt), mas mantém
            // a trava aqui pra não crashar.
            val user = authRepository.currentUser.first { it != null }!!
            prefill(user)
        }
    }

    private fun prefill(user: User) {
        _state.update {
            it.copy(
                email = user.email,
                name = user.name,
                registrationNumber = user.registrationNumber,
                team = user.team,
                shift = user.shift,
                isLoading = false,
            )
        }
    }

    fun onNameChange(v: String) =
        _state.update { it.copy(name = v, error = null, savedMessage = null) }

    fun onRegistrationNumberChange(v: String) =
        _state.update { it.copy(registrationNumber = v, error = null, savedMessage = null) }

    fun onTeamChange(v: String) =
        _state.update { it.copy(team = v, error = null, savedMessage = null) }

    fun onShiftChange(v: Shift) =
        _state.update { it.copy(shift = v, error = null, savedMessage = null) }

    fun save() {
        val c = _state.value
        if (c.name.isBlank()) {
            _state.update { it.copy(error = "Preencha o nome") }
            return
        }
        if (c.registrationNumber.isBlank() || c.team.isBlank()) {
            _state.update { it.copy(error = "Preencha matrícula e equipe") }
            return
        }
        _state.update { it.copy(isSaving = true, error = null, savedMessage = null) }
        viewModelScope.launch {
            val result = authRepository.updateProfile(
                name = c.name.trim(),
                registrationNumber = c.registrationNumber.trim(),
                team = c.team.trim(),
                shift = c.shift,
            )
            when (result) {
                is AuthResult.Success -> _state.update {
                    it.copy(isSaving = false, savedMessage = "Perfil atualizado")
                }
                is AuthResult.Failure -> _state.update {
                    it.copy(isSaving = false, error = result.message)
                }
            }
        }
    }
}
