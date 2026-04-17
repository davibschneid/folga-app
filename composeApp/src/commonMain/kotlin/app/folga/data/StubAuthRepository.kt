package app.folga.data

import app.folga.domain.AuthRepository
import app.folga.domain.AuthResult
import app.folga.domain.User
import app.folga.domain.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlin.random.Random

/**
 * In-memory/local auth used during MVP development.
 *
 * Replace with a Firebase-backed implementation (via dev.gitlive.firebase-auth)
 * once `google-services.json` / `GoogleService-Info.plist` are added and a
 * Firebase project is configured. See README for setup steps.
 */
class StubAuthRepository(
    private val userRepository: UserRepository,
) : AuthRepository {

    private val _currentUser = MutableStateFlow<User?>(null)
    override val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val passwords = mutableMapOf<String, String>()

    override suspend fun signInWithEmail(email: String, password: String): AuthResult {
        val stored = passwords[email]
        if (stored == null || stored != password) {
            return AuthResult.Failure("Email ou senha inválidos")
        }
        val user = userRepository.findByEmail(email) ?: return AuthResult.Failure("Usuário não encontrado")
        _currentUser.value = user
        return AuthResult.Success(user)
    }

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        name: String,
        registrationNumber: String,
        team: String,
    ): AuthResult {
        if (userRepository.findByEmail(email) != null) {
            return AuthResult.Failure("Email já cadastrado")
        }
        val user = User(
            id = randomId(),
            email = email,
            name = name,
            registrationNumber = registrationNumber,
            team = team,
            createdAt = Clock.System.now(),
        )
        userRepository.upsert(user)
        passwords[email] = password
        _currentUser.value = user
        return AuthResult.Success(user)
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
