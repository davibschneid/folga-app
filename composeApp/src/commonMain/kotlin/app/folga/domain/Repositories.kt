package app.folga.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface UserRepository {
    suspend fun upsert(user: User)
    suspend fun findById(id: String): User?
    suspend fun findByEmail(email: String): User?
    fun observeAll(): Flow<List<User>>
}

interface FolgaRepository {
    suspend fun reserve(userId: String, date: LocalDate, note: String?): Folga
    suspend fun cancel(id: String)
    suspend fun findById(id: String): Folga?
    fun observeByUser(userId: String): Flow<List<Folga>>
    fun observeAll(): Flow<List<Folga>>
}

interface SwapRepository {
    suspend fun request(
        fromFolgaId: String,
        toFolgaId: String,
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
    ): AuthResult

    /**
     * Sign in with a Google ID token. On Android/iOS the token is obtained via the
     * platform-specific Google Sign-In SDK and then passed to this method.
     */
    suspend fun signInWithGoogleIdToken(
        idToken: String,
        email: String,
        name: String,
    ): AuthResult

    suspend fun signOut()
}
