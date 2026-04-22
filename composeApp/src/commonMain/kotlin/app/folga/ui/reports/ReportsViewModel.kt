package app.folga.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folga.domain.AuthRepository
import app.folga.domain.Folga
import app.folga.domain.FolgaRepository
import app.folga.domain.SwapRepository
import app.folga.domain.SwapRequest
import app.folga.domain.User
import app.folga.domain.UserRepository
import app.folga.domain.UserRole
import app.folga.domain.rules.WorkedDaysReportRow
import app.folga.domain.rules.buildWorkedDaysReport
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
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * Estado da tela de Relatório. `from`/`to` são o período selecionado.
 * Default: últimos 30 dias até hoje. Usuário pode trocar pelo date range
 * picker.
 */
data class ReportsUiState(
    val from: LocalDate,
    val to: LocalDate,
)

class ReportsViewModel(
    authRepository: AuthRepository,
    userRepository: UserRepository,
    folgaRepository: FolgaRepository,
    private val swapRepository: SwapRepository,
) : ViewModel() {

    private val today: LocalDate = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()).date

    private val _state = MutableStateFlow(
        ReportsUiState(
            // Default: últimos 30 dias. Um período razoável pra fechamento
            // mensal sem forçar o admin a mexer no picker toda vez.
            from = today.minus(30, DateTimeUnit.DAY),
            to = today,
        ),
    )
    val state: StateFlow<ReportsUiState> = _state.asStateFlow()

    val currentUser: StateFlow<User?> = authRepository.currentUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val users: StateFlow<List<User>> = userRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val folgas: StateFlow<List<Folga>> = folgaRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Observa trocas relevantes pro relatório. O repositório só expõe
     * observeIncoming/observeOutgoing por usuário (as regras do Firestore
     * só liberam `read` de swap pra participante ou admin):
     *  - ADMIN: no Firestore as regras permitem listar todos os swaps,
     *    mas o repositório atual ainda filtra por participante. Então o
     *    admin vê a linha dele completa e dos outros usuários só quando
     *    ele foi participante. É uma limitação conhecida — pro relatório
     *    global de verdade vai precisar de uma API nova (observeAll) no
     *    SwapRepository.
     *  - USER: vê só a própria linha, o recorte bate perfeitamente.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val swaps: StateFlow<List<SwapRequest>> = currentUser
        .flatMapLatest { u ->
            if (u == null) flowOf(emptyList())
            else combine(
                swapRepository.observeIncoming(u.id),
                swapRepository.observeOutgoing(u.id),
            ) { inc, out -> (inc + out).distinctBy { it.id } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Linhas do relatório já filtradas por visibilidade: ADMIN vê todos,
     * USER vê só a própria linha.
     */
    val rows: StateFlow<List<WorkedDaysReportRow>> = combine(
        users, folgas, swaps, currentUser, _state,
    ) { allUsers, allFolgas, allSwaps, me, s ->
        val report = buildWorkedDaysReport(
            users = allUsers,
            folgas = allFolgas,
            swaps = allSwaps,
            from = s.from,
            to = s.to,
        )
        if (me?.role == UserRole.ADMIN) report
        else report.filter { it.user.id == me?.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onFromChange(value: LocalDate) = _state.update {
        // Se o usuário escolher `from > to`, puxa o `to` junto pra evitar
        // período inválido (a função pura retorna lista vazia nesse caso).
        if (value > it.to) it.copy(from = value, to = value) else it.copy(from = value)
    }

    fun onToChange(value: LocalDate) = _state.update {
        if (value < it.from) it.copy(from = value, to = value) else it.copy(to = value)
    }
}
