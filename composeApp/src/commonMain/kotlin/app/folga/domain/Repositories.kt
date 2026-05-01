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

    /**
     * Atualiza apenas a URL da foto de perfil do usuário logado. A URL
     * vem do [PhotoStorageRepository.upload] depois do upload bem-sucedido
     * pro Firebase Storage. Separado do [updateProfile] porque a foto é
     * alterada por um fluxo diferente (image picker + upload) e acontece
     * independente da edição dos outros campos.
     */
    suspend fun updatePhotoUrl(url: String?): AuthResult

    suspend fun signOut()

    /**
     * Garante que a camada de Firestore está pronta pra receber
     * leituras/escritas. Necessário porque o [signOut] desabilita a
     * rede do Firestore globalmente (corta listeners pra evitar
     * PERMISSION_DENIED pós-logout). Fluxos que rodam DESLOGADOS e
     * batem no Firestore — como o "Esqueci minha senha", que faz
     * `isAllowed` em [AllowedEmailRepository] antes de qualquer
     * sign-in — precisam chamar isso primeiro pra reativar a rede.
     * Os caminhos de signIn já chamam internamente. Idempotente.
     */
    suspend fun ensureFirestoreReady()

    /**
     * Dispara o e-mail de reset de senha do Firebase Auth pra [email].
     * Pré-condição: o e-mail precisa existir como doc no `users` (gate
     * aplicado pelo [LoginViewModel] antes de chamar) — assim a gente
     * evita usar o reset como oráculo de "esse e-mail tem conta?".
     *
     * Retorna [AuthResult.Success] (com um User dummy só pra reaproveitar
     * o sealed type) quando o Firebase aceitou o pedido. Sucesso aqui
     * só significa "e-mail enfileirado", não que o usuário leu/clicou.
     */
    suspend fun sendPasswordResetEmail(email: String): AuthResult
}

/**
 * Upload de foto de perfil pro Firebase Storage. Recebe os bytes da
 * imagem selecionada pelo usuário (picker nativo de cada plataforma) e
 * devolve a URL pública de download que depois é persistida no doc do
 * usuário via [AuthRepository.updatePhotoUrl].
 *
 * Camada de persistência: `profile_photos/{uid}.jpg` no bucket default
 * do projeto. As regras do Storage precisam permitir leitura pública
 * e escrita só pelo próprio usuário autenticado — a URL retornada já
 * contém o token do Storage para acesso público.
 */
interface PhotoStorageRepository {
    suspend fun upload(userId: String, bytes: ByteArray): String
}

/**
 * Consulta de feriados nacionais via API externa usando Ktor.
 * Auxilia o usuário a identificar dias de folga estratégicos.
 */
interface HolidayRepository {
    suspend fun getHolidays(year: Int): List<Holiday>
}

data class Holiday(
    val date: LocalDate,
    val name: String,
    val type: String
)
