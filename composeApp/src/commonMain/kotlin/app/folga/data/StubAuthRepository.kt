package app.folga.data

import app.folga.db.FolgaDatabase
import app.folga.domain.AuthRepository
import app.folga.domain.AuthResult
import app.folga.domain.User
import app.folga.domain.UserRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlin.random.Random

/**
 * In-memory/local auth used during MVP development.
 *
 * Credentials are persisted via SQLDelight (`CredentialEntity`) so the stub
 * keeps working across app restarts. The whole class (and the credentials
 * table) must be removed when Firebase Auth replaces the stub — see README.
 */
class StubAuthRepository(
    private val db: FolgaDatabase,
    private val userRepository: UserRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AuthRepository {

    private val _currentUser = MutableStateFlow<User?>(null)
    override val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val queries get() = db.folgaQueries

    override suspend fun signInWithEmail(email: String, password: String): AuthResult = withContext(dispatcher) {
        val stored = queries.selectCredentialByEmail(email).executeAsOneOrNull()
        if (stored == null || stored.password != password) {
            return@withContext AuthResult.Failure("Email ou senha inválidos")
        }
        val user = userRepository.findByEmail(email)
            ?: return@withContext AuthResult.Failure("Usuário não encontrado")
        _currentUser.value = user
        AuthResult.Success(user)
    }

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        name: String,
        registrationNumber: String,
        team: String,
    ): AuthResult = withContext(dispatcher) {
        if (userRepository.findByEmail(email) != null) {
            return@withContext AuthResult.Failure("Email já cadastrado")
        }
        val user = User(
            id = randomId(),
            email = email,
            name = name,
            registrationNumber = registrationNumber,
            team = team,
            createdAt = Clock.System.now(),
        )
        db.transaction {
            queries.upsertUser(
                id = user.id,
                email = user.email,
                name = user.name,
                registrationNumber = user.registrationNumber,
                team = user.team,
                createdAt = user.createdAt.toEpochMilliseconds(),
            )
            queries.upsertCredential(email = email, password = password)
        }
        _currentUser.value = user
        AuthResult.Success(user)
    }

    override suspend fun signInWithGoogleIdToken(
        idToken: String,
        email: String,
        name: String,
    ): AuthResult {
        val existing = userRepository.findByEmail(email)
        val user = existing ?: User(
            id = randomId(),
            email = email,
            name = name,
            registrationNumber = "",
            team = "",
            createdAt = Clock.System.now(),
        ).also { userRepository.upsert(it) }
        _currentUser.value = user
        return AuthResult.Success(user)
    }

    override suspend fun signOut() {
        _currentUser.value = null
    }

    private fun randomId(): String = buildString {
        repeat(16) {
            val c = Random.nextInt(36)
            append(if (c < 10) ('0'.code + c).toChar() else ('a'.code + (c - 10)).toChar())
        }
    }
}
