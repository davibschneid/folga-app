package app.folga.ui.folgas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folga.domain.AuthRepository
import app.folga.domain.Folga
import app.folga.domain.FolgaRepository
import app.folga.domain.FolgaStatus
import app.folga.domain.Shift
import app.folga.domain.SwapRepository
import app.folga.domain.SwapRequest
import app.folga.domain.SwapStatus
import app.folga.domain.User
import app.folga.domain.UserRepository
import app.folga.domain.rules.currentSwapPeriod
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

data class FolgasUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val newFolgaDate: String = "",
    val newFolgaNote: String = "",
)

/**
 * Linha de "troca agendada" exibida na tela inicial. Representa uma troca
 * unidirecional que envolve o usuário atual: o [requesterName] cadastrou
 * o dia [date] e está pedindo (ou já conseguiu) que [targetName] assuma
 * esse dia. No modelo unidirecional só existe uma data — a do dia sendo
 * transferido.
 */
data class ScheduledSwap(
    val id: String,
    val status: SwapStatus,
    val requesterName: String,
    val requesterPhotoUrl: String?,
    val requesterShift: Shift?,
    val targetName: String,
    val targetPhotoUrl: String?,
    val targetShift: Shift?,
    val date: LocalDate?,
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
     * Trocas "agendadas" envolvendo o usuário atual. Mostra apenas trocas
     * **confirmadas** (status `ACCEPTED`) cuja data cai no período corrente
     * (dia 16 → dia 15 do mês seguinte). Pedido do cliente: a Home só lista
     * o que de fato vai acontecer no ciclo atual; pendentes ficam na tela
     * de Trocas até serem aceitas/recusadas. Ordenado por data crescente.
     */
    val scheduledSwaps: StateFlow<List<ScheduledSwap>> = combine(
        incoming,
        outgoing,
        allFolgas,
        allUsersRaw,
    ) { inc, out, allF, allU ->
        val userById = allU.associateBy { it.id }
        val folgaById = allF.associateBy { it.id }
        val period = currentSwapPeriod(Clock.System.now())
        val merged = (inc + out).distinctBy { it.id }
        merged
            .filter { swap ->
                if (swap.status != SwapStatus.ACCEPTED) return@filter false
                val date = folgaById[swap.fromFolgaId]?.date ?: return@filter false
                period.contains(date)
            }
            .map { swap ->
                val requester = userById[swap.requesterId]
                val target = userById[swap.targetId]
                ScheduledSwap(
                    id = swap.id,
                    status = swap.status,
                    requesterName = requester?.name ?: swap.requesterId,
                    requesterPhotoUrl = requester?.photoUrl,
                    requesterShift = requester?.shift,
                    targetName = target?.name ?: swap.targetId,
                    targetPhotoUrl = target?.photoUrl,
                    targetShift = target?.shift,
                    date = folgaById[swap.fromFolgaId]?.date,
                )
            }
            .sortedBy { it.date ?: LocalDate(9999, 12, 31) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Contagem de trocas recebidas em status `PENDING` — alimenta o badge
     * do sino no header da Home e da aba "Trocas" na barra inferior.
     * Derivado do mesmo `incoming` que já é observado pela tela de Trocas,
     * então não adiciona listener extra no Firestore.
     */
    val pendingIncomingCount: StateFlow<Int> = incoming
        .map { list -> list.count { it.status == SwapStatus.PENDING } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

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
        // D+1 mínimo: o picker já bloqueia datas <= hoje, mas mantemos a
        // checagem aqui como defesa em profundidade (state pode ser
        // setado via outro caminho). "Hoje" é o dia local do dispositivo.
        val tomorrow = Clock.System.todayIn(TimeZone.currentSystemDefault())
            .plus(1, DateTimeUnit.DAY)
        if (date < tomorrow) {
            _state.update {
                it.copy(
                    error = "Selecione uma data a partir de amanhã.",
                    successMessage = null,
                )
            }
            return
        }
        // Bloqueia duplicata: se o usuário já tem uma folga ativa
        // (qualquer status que não seja CANCELLED) na mesma data, não
        // permite criar outra. Inclui SCHEDULED (cadastrou direto),
        // SWAPPED (assumiu via troca aceita) e COMPLETED (já passou).
        // Pedido literal do cliente: "Dia de trabalho já registrado.".
        val existing = folgas.value.any { f ->
            f.date == date && f.status != FolgaStatus.CANCELLED
        }
        if (existing) {
            _state.update {
                it.copy(
                    error = "Dia de trabalho já registrado.",
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
