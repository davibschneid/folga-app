package app.folga.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folga.domain.AuthRepository
import app.folga.domain.AuthResult
import app.folga.domain.PhotoStorageRepository
import app.folga.domain.Shift
import app.folga.domain.SwapRepository
import app.folga.domain.SwapStatus
import app.folga.domain.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val email: String = "",
    val name: String = "",
    val registrationNumber: String = "",
    val team: String = "",
    val shift: Shift = Shift.MANHA,
    val photoUrl: String? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isUploadingPhoto: Boolean = false,
    val error: String? = null,
    val savedMessage: String? = null,
    val pendingSwapsCount: Int = 0,
)

/**
 * ViewModel da tela de edição de perfil. Além dos campos editáveis
 * (nome/matrícula/equipe/turno), agora cuida também do upload da foto
 * de perfil — ao receber os bytes do image picker, faz upload pro
 * [PhotoStorageRepository] e persiste a URL via
 * [AuthRepository.updatePhotoUrl].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val photoStorage: PhotoStorageRepository,
    private val swapRepository: SwapRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState(isLoading = true))
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    // Count pendentes — pra manter o badge consistente nas três telas.
    private val pendingIncoming: StateFlow<Int> = authRepository.currentUser
        .filterNotNull()
        .flatMapLatest { user -> swapRepository.observeIncoming(user.id) }
        .map { list -> list.count { it.status == SwapStatus.PENDING } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        viewModelScope.launch {
            val user = authRepository.currentUser.first { it != null }!!
            prefill(user)
        }
        viewModelScope.launch {
            pendingIncoming.collect { count ->
                _state.update { it.copy(pendingSwapsCount = count) }
            }
        }
        viewModelScope.launch {
            // Sincroniza photoUrl com o user atual sempre que ele muda —
            // outros fluxos (ex.: outro device) podem alterar a foto.
            authRepository.currentUser.filterNotNull().collect { u ->
                _state.update { it.copy(photoUrl = u.photoUrl) }
            }
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
                photoUrl = user.photoUrl,
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

    /**
     * Chamado pelo `rememberImagePicker` quando o usuário seleciona uma
     * imagem. Sobe os bytes pro Firebase Storage, pega a URL pública e
     * atualiza o documento do usuário — se qualquer passo falhar, mostra
     * erro inline sem quebrar a tela.
     */
    fun pickPhoto(bytes: ByteArray) {
        if (_state.value.isUploadingPhoto) return
        _state.update { it.copy(isUploadingPhoto = true, error = null, savedMessage = null) }
        viewModelScope.launch {
            runCatching {
                val user = authRepository.currentUser.first { it != null }!!
                val url = photoStorage.upload(user.id, bytes)
                when (val r = authRepository.updatePhotoUrl(url)) {
                    is AuthResult.Success -> url
                    is AuthResult.Failure -> error(r.message)
                }
            }.onSuccess { url ->
                _state.update {
                    it.copy(
                        isUploadingPhoto = false,
                        photoUrl = url,
                        savedMessage = "Foto atualizada",
                    )
                }
            }.onFailure { t ->
                // Aplica a mesma invariante do save(): se vai setar `error`,
                // limpa `savedMessage` — senão o sucesso de um save que
                // rodou em paralelo com o upload fica junto com o erro.
                _state.update {
                    it.copy(
                        isUploadingPhoto = false,
                        error = t.message ?: "Falha ao enviar a foto",
                        savedMessage = null,
                    )
                }
            }
        }
    }

    fun save() {
        val c = _state.value
        // Invariante: todo caminho que seta `error` também limpa
        // `savedMessage`. Sem isso a UI pode renderizar erro e sucesso ao
        // mesmo tempo — cenário real: upload de foto seta
        // `savedMessage = "Foto atualizada"` e em seguida o usuário apaga
        // o nome e clica Salvar (flaggeado no PR #25 pelo Devin Review).
        if (c.name.isBlank()) {
            _state.update { it.copy(error = "Preencha o nome", savedMessage = null) }
            return
        }
        if (c.registrationNumber.isBlank() || c.team.isBlank()) {
            _state.update { it.copy(error = "Preencha matrícula e equipe", savedMessage = null) }
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
                    // Mesma invariante: erro nunca coexiste com
                    // savedMessage (pode ter sido setado pelo pickPhoto
                    // que rodou em paralelo).
                    it.copy(
                        isSaving = false,
                        error = result.message,
                        savedMessage = null,
                    )
                }
            }
        }
    }

}
