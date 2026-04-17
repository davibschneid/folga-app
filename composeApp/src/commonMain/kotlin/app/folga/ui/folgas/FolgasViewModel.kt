package app.folga.ui.folgas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folga.domain.AuthRepository
import app.folga.domain.Folga
import app.folga.domain.FolgaRepository
import app.folga.domain.User
import app.folga.domain.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class FolgasUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val newFolgaDate: String = "",
    val newFolgaNote: String = "",
)

class FolgasViewModel(
    authRepository: AuthRepository,
    private val folgaRepository: FolgaRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FolgasUiState())
    val state: StateFlow<FolgasUiState> = _state.asStateFlow()

    val currentUser: StateFlow<User?> = authRepository.currentUser.let { flow ->
        if (flow is StateFlow<*>) @Suppress("UNCHECKED_CAST") (flow as StateFlow<User?>)
        else MutableStateFlow<User?>(null).asStateFlow()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val folgas: StateFlow<List<Folga>> = currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(emptyList<Folga>())
            else folgaRepository.observeByUser(user.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allUsers: StateFlow<List<User>> = combine(
        userRepository.observeAll(),
        currentUser,
    ) { users, me -> users.filter { it.id != me?.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onDateChange(v: String) = _state.update { it.copy(newFolgaDate = v, error = null) }
    fun onNoteChange(v: String) = _state.update { it.copy(newFolgaNote = v, error = null) }

    fun reserve() {
        val me = currentUser.value ?: run {
            _state.update { it.copy(error = "Faça login primeiro") }
            return
        }
        val dateStr = _state.value.newFolgaDate.trim()
        val date = runCatching { LocalDate.parse(dateStr) }.getOrNull()
        if (date == null) {
            _state.update { it.copy(error = "Data inválida. Use o formato AAAA-MM-DD") }
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                folgaRepository.reserve(
                    userId = me.id,
                    date = date,
                    note = _state.value.newFolgaNote.takeIf { it.isNotBlank() },
                )
            }.onSuccess {
                _state.update { it.copy(isLoading = false, newFolgaDate = "", newFolgaNote = "") }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message ?: "Erro ao reservar folga") }
            }
        }
    }

    fun cancel(folgaId: String) {
        viewModelScope.launch {
            folgaRepository.cancel(folgaId)
        }
    }
}
