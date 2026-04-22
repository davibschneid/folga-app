package app.folga.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface UserRepository {
    suspend fun upsert(user: User)
    suspend fun findById(id: String): User?
    suspend fun findByEmail(email: String): User?
    /**
     * Atualiza apenas o [UserRole] de um usuário já existente. Usado pela
     * tela de Administração para promover/despromover usuários.
     */
    suspend fun updateRole(userId: String, role: UserRole)
    fun observeAll(): Flow<List<User>>
}

/**
 * CRUD da whitelist de e-mails autorizados a se cadastrar / entrar no app.
 * Gerenciado exclusivamente pela tela de Administração. Se o e-mail do
 * usuário não estiver autorizado (nem na whitelist nem no
 * [AdminBootstrap]), o login/cadastro é rejeitado com mensagem clara.
 */
interface AllowedEmailRepository {
    suspend fun isAllowed(email: String): Boolean
    suspend fun add(email: String, addedBy: String)
    suspend fun remove(email: String)
    fun observeAll(): Flow<List<AllowedEmail>>
}

interface FolgaRepository {
    suspend fun reserve(userId: String, date: LocalDate, note: String?): Folga
    suspend fun cancel(id: String)
    suspend fun findById(id: String): Folga?
    fun observeByUser(userId: String): Flow<List<Folga>>
    fun observeAll(): Flow<List<Folga>>
}

interface SwapRepository {
    /**
     * Pedido unidirecional de troca: o requester (dono de [fromFolgaId])
     * está pedindo que [targetId] assuma esse dia. Se aceito, a folga
     * referenciada por [fromFolgaId] tem ownership transferido pro target
     * e status vira [app.folga.domain.FolgaStatus.SWAPPED]. Não há dia de
     * contrapartida — o target só passa a ter um compromisso a mais.
     */
    suspend fun request(
        fromFolgaId: String,
        requesterId: String,
        targetId: String,
        message: String?,
    ): SwapRequest

    suspend fun accept(swapId: String)
    suspend fun reject(swapId: String)
    suspend fun cancel(swapId: String)
    fun observeIncoming(userId: String): Flow<List<SwapRequest>>
    fun observeOutgoing(userId: String): Flow<List<SwapRequest>>
}

sealed interface AuthResult {
    data class Success(val user: User) : AuthResult
    data class Failure(val message: String) : AuthResult
}

interface AuthRepository {
    val currentUser: Flow<User?>
    suspend fun signInWithEmail(email: String, password: String): AuthResult
    suspend fun signUpWithEmail(
        email: String,
        password: String,
        name: String,
        registrationNumber: String,
        team: String,
        shift: Shift,
    ): AuthResult

    /**
     * Sign in with a Google ID token. On Android/iOS the token is obtained via the
     * platform-specific Google Sign-In SDK and then passed to this method.
     * For brand-new Google users this creates a minimal profile with empty
     * matrícula/equipe — the UI then routes the user to a "completar cadastro"
     * screen that calls [completeProfile] before letting them into the app.
     */
    suspend fun signInWithGoogleIdToken(
        idToken: String,
        email: String,
        name: String,
    ): AuthResult

    /**
     * Fills in the fields that Google Sign-In can't supply on its own
     * (matrícula, equipe, turno) for the currently signed-in user.
     */
    suspend fun completeProfile(
        registrationNumber: String,
        team: String,
        shift: Shift,
    ): AuthResult

    /**
     * Atualiza os campos editáveis do perfil do usuário logado: nome,
     * matrícula, equipe e turno. E-mail e role não são editáveis por aqui
     * — e-mail é imutável pelo Firebase Auth e role só muda via
     * tela de Administração.
     */
    suspend fun updateProfile(
        name: String,
        registrationNumber: String,
        team: String,
        shift: Shift,
    ): AuthResult

    suspend fun signOut()
}
