package app.folga.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folga.domain.AdminBootstrap
import app.folga.domain.AllowedEmail
import app.folga.domain.AllowedEmailRepository
import app.folga.domain.AuthRepository
import app.folga.domain.User
import app.folga.domain.UserRepository
import app.folga.domain.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel da tela de Administração. Expõe:
 *  - lista de todos os usuários (com role atual);
 *  - lista da whitelist de e-mails (+ quem adicionou / quando);
 *  - ações pra promover/despromover usuários e incluir/remover e-mails.
 *
 * As ações pedem o usuário logado (admin atual) pra que o campo `addedBy`
 * da whitelist mostre quem autorizou cada e-mail — útil pra auditoria.
 */
class AdminViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val allowedEmailRepository: AllowedEmailRepository,
) : ViewModel() {

    val users: StateFlow<List<User>> = userRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val allowedEmails: StateFlow<List<AllowedEmail>> = allowedEmailRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val currentUser: StateFlow<User?> = authRepository.currentUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _isError = MutableStateFlow(false)
    val isError: StateFlow<Boolean> = _isError.asStateFlow()

    private val _newEmail = MutableStateFlow("")
    val newEmail: StateFlow<String> = _newEmail.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun onNewEmailChange(value: String) {
        _newEmail.value = value
    }

    fun clearMessage() {
        _message.value = null
        _isError.value = false
    }

    /**
     * Alterna a role do usuário [target]. Bloqueia a troca nos dois casos
     * irrecuperáveis:
     *  - alterar a própria role (admin pode se auto-demotar e trancar o
     *    acesso à tela);
     *  - despromover um admin bootstrap (eles sempre têm acesso, ficaria
     *    inconsistente).
     */
    fun toggleRole(target: User) {
        val current = currentUser.value ?: return
        if (target.id == current.id) {
            _isError.value = true
            _message.value = "Você não pode alterar seu próprio perfil."
            return
        }
        if (AdminBootstrap.isBootstrapAdmin(target.email) && target.role == UserRole.ADMIN) {
            _isError.value = true
            _message.value = "Este admin não pode ser despromovido (bootstrap)."
            return
        }
        val newRole = if (target.role == UserRole.ADMIN) UserRole.USER else UserRole.ADMIN
        viewModelScope.launch {
            runCatching { userRepository.updateRole(target.id, newRole) }
                .onSuccess {
                    _isError.value = false
                    _message.value = if (newRole == UserRole.ADMIN) {
                        "${target.name} agora é admin."
                    } else {
                        "${target.name} voltou a ser usuário."
                    }
                }
                .onFailure {
                    _isError.value = true
                    _message.value = it.message ?: "Erro ao atualizar perfil."
                }
        }
    }

    fun addEmail() {
        val raw = _newEmail.value
        if (!looksLikeEmail(raw)) {
            _isError.value = true
            _message.value = "E-mail inválido."
            return
        }
        val normalized = AdminBootstrap.normalize(raw)
        val currentEmail = currentUser.value?.email ?: ""
        viewModelScope.launch {
            _isLoading.value = true
            runCatching { allowedEmailRepository.add(normalized, addedBy = currentEmail) }
                .onSuccess {
                    _newEmail.value = ""
                    _isError.value = false
                    _message.value = "E-mail $normalized autorizado."
                }
                .onFailure {
                    _isError.value = true
                    _message.value = it.message ?: "Erro ao autorizar e-mail."
                }
            _isLoading.value = false
        }
    }

    fun removeEmail(email: String) {
        val normalized = AdminBootstrap.normalize(email)
        if (normalized in AdminBootstrap.ADMIN_EMAILS) {
            _isError.value = true
            _message.value = "Admin bootstrap não pode ser removido."
            return
        }
        viewModelScope.launch {
            runCatching { allowedEmailRepository.remove(normalized) }
                .onSuccess {
                    _isError.value = false
                    _message.value = "E-mail $normalized removido."
                }
                .onFailure {
                    _isError.value = true
                    _message.value = it.message ?: "Erro ao remover e-mail."
                }
        }
    }

    private fun looksLikeEmail(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return false
        val at = trimmed.indexOf('@')
        if (at <= 0 || at == trimmed.lastIndex) return false
        val dotAfterAt = trimmed.indexOf('.', at)
        return dotAfterAt > at && dotAfterAt < trimmed.lastIndex
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
