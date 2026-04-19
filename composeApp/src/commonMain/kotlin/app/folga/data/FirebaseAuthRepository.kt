package app.folga.data

import app.folga.domain.AuthRepository
import app.folga.domain.AuthResult
import app.folga.domain.User
import app.folga.domain.UserRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseAuthException
import dev.gitlive.firebase.auth.FirebaseAuthInvalidCredentialsException
import dev.gitlive.firebase.auth.FirebaseAuthInvalidUserException
import dev.gitlive.firebase.auth.FirebaseAuthUserCollisionException
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.Clock

/**
 * Firebase-backed [AuthRepository] used for email/password sign-in and sign-up.
 *
 * Identity (uid + email) lives in Firebase Auth. The app-specific profile
 * fields (name, matrícula, team, createdAt) are still stored in
 * [UserRepository] — for now SQLite locally; a follow-up PR will move that to
 * Firestore so the profile is shared across devices.
 */
class FirebaseAuthRepository(
    private val userRepository: UserRepository,
    private val auth: FirebaseAuth = Firebase.auth,
) : AuthRepository {

    // Set synchronously at the end of a successful sign-in/sign-up so the UI
    // sees the resolved domain User even before `authStateChanged` re-emits
    // (it may have already fired with a null local profile because the SQLite
    // upsert hadn't landed yet).
    private val manualUser = MutableStateFlow<User?>(null)

    override val currentUser: Flow<User?> = combine(
        auth.authStateChanged,
        manualUser,
    ) { fbUser, manual ->
        when {
            fbUser == null -> null
            manual != null && manual.id == fbUser.uid -> manual
            else -> resolveProfile(fbUser)
        }
    }

    override suspend fun signInWithEmail(email: String, password: String): AuthResult =
        runCatching { auth.signInWithEmailAndPassword(email, password) }
            .fold(
                onSuccess = { result ->
                    val fbUser = result.user
                        ?: return@fold AuthResult.Failure("Falha ao autenticar")
                    val profile = resolveProfile(fbUser)
                        ?: return@fold AuthResult.Failure(
                            "Perfil não encontrado. Conclua o cadastro antes de entrar."
                        )
                    manualUser.value = profile
                    AuthResult.Success(profile)
                },
                onFailure = { AuthResult.Failure(it.humanMessage("Email ou senha inválidos")) },
            )

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        name: String,
        registrationNumber: String,
        team: String,
    ): AuthResult = runCatching { auth.createUserWithEmailAndPassword(email, password) }
        .fold(
            onSuccess = { result ->
                val fbUser = result.user
                    ?: return@fold AuthResult.Failure("Falha ao criar usuário")
                val profile = User(
                    id = fbUser.uid,
                    email = fbUser.email ?: email,
                    name = name,
                    registrationNumber = registrationNumber,
                    team = team,
                    createdAt = Clock.System.now(),
                )
                // Persist first, then publish manually so currentUser emits the
                // complete profile even though authStateChanged already fired.
                userRepository.upsert(profile)
                manualUser.value = profile
                AuthResult.Success(profile)
            },
            onFailure = { AuthResult.Failure(it.humanMessage("Erro ao cadastrar usuário")) },
        )

    override suspend fun signInWithGoogleIdToken(
        idToken: String,
        email: String,
        name: String,
    ): AuthResult =
        // Google Sign-In wiring lives in platform-specific code and will be
        // plugged in a follow-up PR together with the native Google Sign-In SDKs.
        AuthResult.Failure("Login com Google ainda não disponível")

    override suspend fun signOut() {
        runCatching { auth.signOut() }
        manualUser.value = null
    }

    private suspend fun resolveProfile(fbUser: FirebaseUser): User? {
        userRepository.findById(fbUser.uid)?.let { return it }
        val email = fbUser.email ?: return null
        return userRepository.findByEmail(email)
    }

    private fun Throwable.humanMessage(fallback: String): String = when (this) {
        is FirebaseAuthInvalidCredentialsException -> "Email ou senha inválidos"
        is FirebaseAuthInvalidUserException -> "Usuário não encontrado"
        is FirebaseAuthUserCollisionException -> "Email já cadastrado"
        is FirebaseAuthException -> message ?: fallback
        else -> message ?: fallback
    }
}
