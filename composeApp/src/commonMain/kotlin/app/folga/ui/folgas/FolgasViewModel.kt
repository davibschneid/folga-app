package app.folga.ui.folgas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folga.domain.AuthRepository
import app.folga.domain.Folga
import app.folga.domain.FolgaRepository
import app.folga.domain.SwapRepository
import app.folga.domain.SwapRequest
import app.folga.domain.SwapStatus
import app.folga.domain.User
import app.folga.domain.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    val successMessage: String? = null,
    val newFolgaDate: String = "",
    val newFolgaNote: String = "",
)

/**
 * Linha de "troca agendada" exibida na tela inicial. Representa uma troca
 * que envolve o usuário atual (seja como solicitante, seja como alvo).
 * Agrega as datas dos dois dias de trabalho envolvidos e os nomes das
 * duas pessoas.
 */
data class ScheduledSwap(
    val id: String,
    val status: SwapStatus,
    val requesterName: String,
    val targetName: String,
    val requesterDate: LocalDate?,
    val targetDate: LocalDate?,
)

class FolgasViewModel(
    authRepository: AuthRepository,
    private val folgaRepository: FolgaRepository,
    private val userRepository: UserRepository,
    private val swapRepository: SwapRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FolgasUiState())
    val state: StateFlow<FolgasUiState> = _state.asStateFlow()

    // Works for any Flow<User?> (stub or Firebase-backed) — no unsafe downcast.
    val currentUser: StateFlow<User?> = authRepository.currentUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    @OptIn(ExperimentalCoroutinesApi::class)
    private val incoming: StateFlow<List<SwapRequest>> = currentUser
        .flatMapLatest { u -> if (u == null) flowOf(emptyList()) else swapRepository.observeIncoming(u.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val outgoing: StateFlow<List<SwapRequest>> = currentUser
        .flatMapLatest { u -> if (u == null) flowOf(emptyList()) else swapRepository.observeOutgoing(u.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Observa todas as folgas (não só do usuário atual) pra conseguir
     * resolver as datas das folgas envolvidas em trocas onde o usuário é
     * o `requester` OU o `target`. Firestore persistence offline cobre
     * o custo dessa assinatura.
     */
    private val allFolgas: StateFlow<List<Folga>> = folgaRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val allUsersRaw: StateFlow<List<User>> = userRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Trocas "agendadas" envolvendo o usuário atual. Inclui `PENDING` e
     * `ACCEPTED` — as duas representam compromissos reais ou potenciais
     * (pendente = aguardando resposta, aceita = confirmada). `REJECTED`
     * e `CANCELLED` ficam de fora porque não resultam em troca real.
     * Ordenado por data do dia do solicitante pra ajudar o usuário a ver
     * o que está mais próximo.
     */
    val scheduledSwaps: StateFlow<List<ScheduledSwap>> = combine(
        incoming,
        outgoing,
        allFolgas,
        allUsersRaw,
    ) { inc, out, allF, allU ->
        val userById = allU.associateBy { it.id }
        val folgaById = allF.associateBy { it.id }
        val merged = (inc + out).distinctBy { it.id }
        merged
            .filter { it.status == SwapStatus.PENDING || it.status == SwapStatus.ACCEPTED }
            .map { swap ->
                ScheduledSwap(
                    id = swap.id,
                    status = swap.status,
                    requesterName = userById[swap.requesterId]?.name ?: swap.requesterId,
                    targetName = userById[swap.targetId]?.name ?: swap.targetId,
                    requesterDate = folgaById[swap.fromFolgaId]?.date,
                    targetDate = folgaById[swap.toFolgaId]?.date,
                )
            }
            .sortedBy { it.requesterDate ?: LocalDate(9999, 12, 31) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onDateChange(v: String) = _state.update {
        it.copy(newFolgaDate = v, error = null, successMessage = null)
    }
    fun onNoteChange(v: String) = _state.update {
        it.copy(newFolgaNote = v, error = null, successMessage = null)
    }

    fun reserve() {
        val me = currentUser.value ?: run {
            // Todos os paths que setam `error` devem também limpar
            // `successMessage` pra a UI não renderizar os dois ao mesmo
            // tempo (flagged pelo Devin Review no PR #16).
            _state.update { it.copy(error = "Faça login primeiro", successMessage = null) }
            return
        }
        val dateStr = _state.value.newFolgaDate.trim()
        val date = runCatching { LocalDate.parse(dateStr) }.getOrNull()
        if (date == null) {
            _state.update {
                it.copy(
                    error = "Selecione uma data válida no calendário",
                    successMessage = null,
                )
            }
            return
        }
        _state.update { it.copy(isLoading = true, error = null, successMessage = null) }
        viewModelScope.launch {
            runCatching {
                folgaRepository.reserve(
                    userId = me.id,
                    date = date,
                    note = _state.value.newFolgaNote.takeIf { it.isNotBlank() },
                )
            }.onSuccess {
                _state.update {
                    it.copy(
                        isLoading = false,
                        newFolgaDate = "",
                        newFolgaNote = "",
                        successMessage = "Dia de trabalho cadastrado com sucesso",
                        error = null,
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Erro ao cadastrar dia de trabalho",
                        successMessage = null,
                    )
                }
            }
        }
    }

    fun cancel(folgaId: String) {
        viewModelScope.launch {
            folgaRepository.cancel(folgaId)
        }
    }
}
