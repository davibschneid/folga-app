package app.folga.ui.swap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folga.domain.AuthRepository
import app.folga.domain.Folga
import app.folga.domain.FolgaRepository
import app.folga.domain.FolgaStatus
import app.folga.domain.Shift
import app.folga.domain.SwapRepository
import app.folga.domain.SwapRequest
import app.folga.domain.User
import app.folga.domain.UserRepository
import app.folga.domain.rules.countQuotaConsumingSwaps
import app.folga.domain.rules.currentSwapPeriod
import app.folga.domain.rules.swapQuotaFor
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

/**
 * Uso de quota do usuário no período corrente (dia 16→15). Usado pra avisar
 * quando o usuário está no limite de trocas.
 */
data class SwapQuotaStatus(
    val shift: Shift,
    val used: Int,
    val quota: Int,
) {
    val remaining: Int get() = (quota - used).coerceAtLeast(0)
    val atOrAboveQuota: Boolean get() = used >= quota
}

data class SwapsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedMyFolgaId: String? = null,
    /**
     * Id do colega (User.id) que o usuário selecionou pra assumir o dia.
     * No modelo unidirecional novo o usuário escolhe *uma pessoa* em vez
     * de um dia específico do colega — o colega só precisa aceitar e
     * passa a assumir o dia do requester.
     */
    val selectedTargetUserId: String? = null,
    val message: String = "",
)

class SwapsViewModel(
    authRepository: AuthRepository,
    private val swapRepository: SwapRepository,
    private val folgaRepository: FolgaRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SwapsUiState())
    val state: StateFlow<SwapsUiState> = _state.asStateFlow()

    // Works for any Flow<User?> (stub or Firebase-backed) — no unsafe downcast.
    val currentUser: StateFlow<User?> = authRepository.currentUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val myFolgas: StateFlow<List<Folga>> = currentUser
        .flatMapLatest { u -> if (u == null) flowOf(emptyList()) else folgaRepository.observeByUser(u.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val users: StateFlow<List<User>> = userRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Todas as folgas do sistema. Usado pra:
     * - Checar conflito de agenda do alvo antes de submeter ([requestSwap]).
     * - Resolver a data exibida nos cards de Recebidas/Enviadas — o
     *   `fromFolgaId` pode pertencer a qualquer usuário (no caso de
     *   incoming) ou ter sido transferido pro target (no caso de outgoing
     *   já aceita), então `myFolgas` (só folgas do usuário logado) não
     *   resolve. `allFolgas` cobre os dois casos.
     */
    val allFolgas: StateFlow<List<Folga>> = folgaRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Lista de colegas disponíveis pra assumir um dia — todos os usuários
     * do sistema exceto o próprio usuário logado e filtrados por
     * compatibilidade de turno (diurno MANHA/TARDE só troca com diurno,
     * NOITE só troca com NOITE). A restrição é uma regra de negócio: a
     * rotina do noturno é incompatível com a do diurno. A mesma validação
     * é reforçada no `requestSwap` (defesa em profundidade caso a UI
     * passe um id inválido) e nas Firestore rules (backend).
     */
    val colleagues: StateFlow<List<User>> = combine(users, currentUser) { all, me ->
        if (me == null) emptyList()
        else all.filter { it.id != me.id && me.shift.isCompatibleWith(it.shift) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val incoming: StateFlow<List<SwapRequest>> = currentUser
        .flatMapLatest { u -> if (u == null) flowOf(emptyList()) else swapRepository.observeIncoming(u.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val outgoing: StateFlow<List<SwapRequest>> = currentUser
        .flatMapLatest { u -> if (u == null) flowOf(emptyList()) else swapRepository.observeOutgoing(u.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Ids das folgas do usuário que estão "aguardando aprovação" — ou
     * seja, são `fromFolgaId` de uma troca PENDENTE iniciada por ele.
     * Usado pra:
     *  - Renderizar badge laranja "Aguardando" ao lado da chip do dia.
     *  - Desabilitar a seleção dessa chip (não dá pra abrir uma 2ª
     *    troca pra um dia que já tem troca pendente em andamento).
     */
    val folgaIdsAwaiting: StateFlow<Set<String>> = outgoing
        .map { list ->
            list.filter { it.status == app.folga.domain.SwapStatus.PENDING }
                .map { it.fromFolgaId }
                .toSet()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /**
     * União de `incoming + outgoing` do usuário atual. Usado em
     * [requestSwap] pra checar se o alvo já é requester ou target de
     * outra troca não-cancelada na mesma data. As regras do Firestore
     * só permitem read de trocas onde o usuário é parte ou é admin
     * (`firestore.rules` linha 199), então a checagem cobre cenários
     * onde o usuário atual também participa de outra troca envolvendo
     * o mesmo colega — que é o caso comum (eu peço troca pra B em D1,
     * depois tento de novo pra D1 com outra folga minha, etc.).
     */
    private val allSwaps: StateFlow<List<SwapRequest>> = combine(
        incoming,
        outgoing,
    ) { inc, out -> (inc + out).distinctBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Contagem de trocas ACEITAS iniciadas pelo usuário no período corrente
     * (dia 16 do mês atual → dia 15 do próximo) vs quota permitida para seu
     * turno (MANHA/TARDE=4, NOITE=3). Regra: só trocas aceitas consomem
     * quota, e só quem iniciou a troca consome quota.
     */
    val quotaStatus: StateFlow<SwapQuotaStatus?> = combine(currentUser, outgoing) { me, out ->
        if (me == null) null
        else {
            val period = currentSwapPeriod(Clock.System.now())
            SwapQuotaStatus(
                shift = me.shift,
                used = countQuotaConsumingSwaps(me.id, out, period),
                quota = swapQuotaFor(me.shift),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectMy(id: String) = _state.update { it.copy(selectedMyFolgaId = id, error = null) }
    fun selectTargetUser(userId: String) =
        _state.update { it.copy(selectedTargetUserId = userId, error = null) }
    fun onMessageChange(v: String) = _state.update { it.copy(message = v, error = null) }

    /**
     * Solicita uma troca. Bloqueia (não submete) se o usuário já está no
     * limite de trocas aceitas iniciadas no período — a UI desabilita o
     * botão nesse caso, mas mantemos a checagem aqui como defesa-em-
     * profundidade pra casos de race condition (a quota pode ter batido
     * o limite depois do último render).
     */
    fun requestSwap() {
        val me = currentUser.value ?: return
        val myId = _state.value.selectedMyFolgaId
        val targetUserId = _state.value.selectedTargetUserId
        if (myId == null || targetUserId == null) {
            _state.update { it.copy(error = "Selecione um dia seu e um colega") }
            return
        }
        // Defesa em profundidade: a UI já filtra `colleagues` por
        // compatibilidade de turno, mas se por qualquer motivo um id
        // incompatível for parar no state (race com refresh da lista,
        // por exemplo) rejeitamos antes de chamar o backend.
        val target = users.value.firstOrNull { it.id == targetUserId }
        if (target != null && !me.shift.isCompatibleWith(target.shift)) {
            _state.update {
                it.copy(
                    error = "Só é possível trocar com colegas do mesmo " +
                        "grupo de turno (diurno ou noturno).",
                )
            }
            return
        }
        val quota = quotaStatus.value
        if (quota != null && quota.atOrAboveQuota) {
            _state.update {
                it.copy(
                    error = "Limite de ${quota.quota} trocas atingido no período. " +
                        "Aguarde o próximo período (dia 16) para solicitar novas.",
                )
            }
            return
        }
        // Conflito de agenda do alvo: se o colega selecionado já tem
        // qualquer compromisso (folga ou troca) pra mesma data da folga
        // que estou cedendo, ele não pode aceitar — não dá pra trabalhar
        // dois turnos no mesmo dia.
        //
        // Cobertura:
        //  - Folga do alvo na mesma data (status != CANCELLED). Inclui
        //    SCHEDULED (cadastrou direto) ou SWAPPED (já assumiu via
        //    outra troca aceita). Se a folga em questão é a própria que
        //    estou cedendo (`myId`), não conta — é o objeto da troca.
        //  - Troca PENDING ou ACCEPTED onde o alvo é requester OU target,
        //    na mesma data. PENDING = compromisso vivo aguardando aceite.
        //    ACCEPTED = já confirmado. REJECTED e CANCELLED não contam:
        //    REJECTED só registra que o alvo recusou — não é compromisso
        //    real, então uma nova tentativa pode ser válida; CANCELLED
        //    foi explicitamente desfeita pelo requester.
        val myFolga = allFolgas.value.firstOrNull { it.id == myId }
        if (myFolga != null) {
            val folgaConflict = allFolgas.value.any { f ->
                f.id != myId &&
                    f.userId == targetUserId &&
                    f.date == myFolga.date &&
                    f.status != FolgaStatus.CANCELLED
            }
            val swapConflict = allSwaps.value.any { s ->
                val blocking = s.status == app.folga.domain.SwapStatus.PENDING ||
                    s.status == app.folga.domain.SwapStatus.ACCEPTED
                if (!blocking) return@any false
                if (s.requesterId != targetUserId && s.targetId != targetUserId) return@any false
                val swapDate = allFolgas.value.firstOrNull { it.id == s.fromFolgaId }?.date
                swapDate == myFolga.date
            }
            if (folgaConflict || swapConflict) {
                _state.update {
                    it.copy(
                        error = "O colega selecionado já tem um agendamento ou " +
                            "troca em aberto para essa data e não pode assumir " +
                            "a solicitação.",
                    )
                }
                return
            }
        }
        submitSwap(me.id, myId, targetUserId)
    }

    private fun submitSwap(requesterId: String, myFolgaId: String, targetUserId: String) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                swapRepository.request(
                    fromFolgaId = myFolgaId,
                    requesterId = requesterId,
                    targetId = targetUserId,
                    message = _state.value.message.takeIf { it.isNotBlank() },
                )
            }.onSuccess {
                _state.update {
                    it.copy(
                        isLoading = false,
                        selectedMyFolgaId = null,
                        selectedTargetUserId = null,
                        message = "",
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message ?: "Erro ao solicitar troca") }
            }
        }
    }

    fun accept(swapId: String) {
        viewModelScope.launch {
            runCatching { swapRepository.accept(swapId) }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Erro ao aceitar troca") } }
        }
    }

    fun reject(swapId: String) {
        viewModelScope.launch {
            runCatching { swapRepository.reject(swapId) }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Erro ao recusar troca") } }
        }
    }

    fun cancel(swapId: String) {
        viewModelScope.launch {
            runCatching { swapRepository.cancel(swapId) }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Erro ao cancelar troca") } }
        }
    }
}
