package app.folga.ui.swap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folga.domain.AuthRepository
import app.folga.domain.Folga
import app.folga.domain.FolgaRepository
import app.folga.domain.Shift
import app.folga.domain.SwapRepository
import app.folga.domain.SwapRequest
import app.folga.domain.User
import app.folga.domain.UserRepository
import app.folga.domain.rules.countAcceptedInitiatedSwaps
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
    val selectedTargetFolgaId: String? = null,
    val message: String = "",
    /**
     * Quando setado, a tela mostra um diálogo de confirmação avisando que
     * a quota foi atingida. O usuário pode confirmar (vai em frente com a
     * solicitação) ou cancelar.
     */
    val quotaWarning: SwapQuotaStatus? = null,
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val colleagueFolgas: StateFlow<List<Folga>> = currentUser
        .flatMapLatest { u ->
            if (u == null) flowOf(emptyList())
            else folgaRepository.observeAll()
        }
        .combine(currentUser) { all, me -> all.filter { it.userId != me?.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val users: StateFlow<List<User>> = userRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val incoming: StateFlow<List<SwapRequest>> = currentUser
        .flatMapLatest { u -> if (u == null) flowOf(emptyList()) else swapRepository.observeIncoming(u.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val outgoing: StateFlow<List<SwapRequest>> = currentUser
        .flatMapLatest { u -> if (u == null) flowOf(emptyList()) else swapRepository.observeOutgoing(u.id) }
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
                used = countAcceptedInitiatedSwaps(me.id, out, period),
                quota = swapQuotaFor(me.shift),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectMy(id: String) = _state.update { it.copy(selectedMyFolgaId = id, error = null) }
    fun selectTarget(id: String) = _state.update { it.copy(selectedTargetFolgaId = id, error = null) }
    fun onMessageChange(v: String) = _state.update { it.copy(message = v, error = null) }

    /**
     * Primeiro passo da solicitação. Valida input, checa quota e — se o
     * usuário já está no limite de trocas aceitas iniciadas no período —
     * pede confirmação via [SwapsUiState.quotaWarning] em vez de submeter
     * direto. A quota só é *avisada*, nunca bloqueia.
     */
    fun requestSwap() {
        val me = currentUser.value ?: return
        val myId = _state.value.selectedMyFolgaId
        val targetId = _state.value.selectedTargetFolgaId
        if (myId == null || targetId == null) {
            _state.update { it.copy(error = "Selecione sua folga e a folga do colega") }
            return
        }
        val quota = quotaStatus.value
        if (quota != null && quota.atOrAboveQuota) {
            // Não bloqueia — só avisa. O usuário pode confirmar mesmo assim.
            _state.update { it.copy(error = null, quotaWarning = quota) }
            return
        }
        submitSwap()
    }

    /** Chamado pelo botão "Confirmar" do diálogo de aviso de quota. */
    fun confirmOverQuota() {
        _state.update { it.copy(quotaWarning = null) }
        submitSwap()
    }

    /** Chamado pelo botão "Cancelar" do diálogo de aviso de quota. */
    fun dismissQuotaWarning() {
        _state.update { it.copy(quotaWarning = null) }
    }

    private fun submitSwap() {
        val me = currentUser.value ?: return
        val myId = _state.value.selectedMyFolgaId ?: return
        val targetId = _state.value.selectedTargetFolgaId ?: return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val targetFolga = folgaRepository.findById(targetId)
                    ?: error("Folga do colega não encontrada")
                swapRepository.request(
                    fromFolgaId = myId,
                    toFolgaId = targetId,
                    requesterId = me.id,
                    targetId = targetFolga.userId,
                    message = _state.value.message.takeIf { it.isNotBlank() },
                )
            }.onSuccess {
                _state.update {
                    it.copy(
                        isLoading = false,
                        selectedMyFolgaId = null,
                        selectedTargetFolgaId = null,
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
