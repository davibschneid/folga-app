package app.folga.data

import app.folga.domain.AdminBootstrap
import app.folga.domain.AllowedEmailRepository
import app.folga.domain.AuthRepository
import app.folga.domain.AuthResult
import app.folga.domain.Shift
import app.folga.domain.User
import app.folga.domain.UserRepository
import app.folga.domain.UserRole
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseAuthException
import dev.gitlive.firebase.auth.FirebaseAuthInvalidCredentialsException
import dev.gitlive.firebase.auth.FirebaseAuthInvalidUserException
import dev.gitlive.firebase.auth.FirebaseAuthUserCollisionException
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.GoogleAuthProvider
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
    private val allowedEmailRepository: AllowedEmailRepository,
    private val auth: FirebaseAuth = Firebase.auth,
) : AuthRepository {

    /**
     * Gate aplicado a todos os fluxos de entrada (signup email/senha,
     * signin email/senha, signin com Google). Se o e-mail não está
     * autorizado, nenhum outro side-effect acontece. Admins bootstrap
     * passam direto mesmo sem estar na collection `allowed_emails`.
     */
    private suspend fun checkEmailAllowed(email: String): AuthResult.Failure? {
        if (allowedEmailRepository.isAllowed(email)) return null
        return AuthResult.Failure(
            "Não autorizado. Solicite ao administrador que libere este e-mail."
        )
    }

    /** Role inicial na primeira criação do perfil. */
    private fun initialRoleFor(email: String): UserRole =
        if (AdminBootstrap.isBootstrapAdmin(email)) UserRole.ADMIN else UserRole.USER

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

    override suspend fun signInWithEmail(email: String, password: String): AuthResult = runCatching {
        // Gate antes de qualquer side-effect: se o admin revogou o e-mail,
        // mesmo tendo cadastro no Firebase Auth o login é bloqueado.
        // `checkEmailAllowed` pode lançar no Firestore (rede/permissão).
        // Precisamos de um runCatching local aqui pra que essa exceção
        // não caia no `.getOrElse` externo e seja mapeada como "Email ou
        // senha inválidos" — feedback enganoso pro usuário.
        runCatching { checkEmailAllowed(email) }
            .getOrElse { ex ->
                return@runCatching AuthResult.Failure(
                    ex.message ?: "Erro ao verificar autorização. Tente novamente."
                )
            }
            ?.let { return@runCatching it }

        val result = auth.signInWithEmailAndPassword(email, password)
        val fbUser = result.user ?: return@runCatching AuthResult.Failure("Falha ao autenticar")
        val profile = resolveProfile(fbUser)
            ?: return@runCatching AuthResult.Failure(
                "Perfil não encontrado. Conclua o cadastro antes de entrar."
            )
        manualUser.value = profile
        AuthResult.Success(profile)
    }.getOrElse { AuthResult.Failure(it.humanMessage("Email ou senha inválidos")) }

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        name: String,
        registrationNumber: String,
        team: String,
        shift: Shift,
    ): AuthResult {
        // Gate ANTES de criar conta no Firebase Auth — senão o e-mail não
        // autorizado ficaria com conta órfã no Auth (gastando quota e
        // bloqueando retry). `checkEmailAllowed` faz I/O no Firestore, que
        // pode lançar (rede/permissão) — runCatching aqui evita que essa
        // exceção suba pro viewModelScope.launch e crashe o app.
        runCatching { checkEmailAllowed(email) }
            .getOrElse {
                return AuthResult.Failure(
                    it.message ?: "Erro ao verificar autorização. Tente novamente."
                )
            }
            ?.let { return it }

        val fbUser = runCatching { auth.createUserWithEmailAndPassword(email, password).user }
            .getOrElse {
                return AuthResult.Failure(it.humanMessage("Erro ao cadastrar usuário"))
            }
            ?: return AuthResult.Failure("Falha ao criar usuário")

        val profile = User(
            id = fbUser.uid,
            email = fbUser.email ?: email,
            name = name,
            registrationNumber = registrationNumber,
            team = team,
            shift = shift,
            role = initialRoleFor(fbUser.email ?: email),
            createdAt = Clock.System.now(),
        )

        // Persist first, then publish manually so currentUser emits the
        // complete profile even though authStateChanged already fired.
        return runCatching {
            userRepository.upsert(profile)
            manualUser.value = profile
            AuthResult.Success(profile)
        }.getOrElse { upsertError ->
            // Local profile write failed after Firebase already created the account.
            // Roll the Firebase user back so the email is free to retry — otherwise
            // the user would be locked out: "email já cadastrado" on retry and
            // "perfil não encontrado" on sign-in.
            runCatching { fbUser.delete() }
            AuthResult.Failure(
                upsertError.message ?: "Erro ao salvar perfil. Tente novamente."
            )
        }
    }

    override suspend fun signInWithGoogleIdToken(
        idToken: String,
        email: String,
        name: String,
    ): AuthResult = runCatching {
        // Exchange the Google ID token for a Firebase credential. `null`
        // access token is the supported value when using Credential Manager /
        // GoogleSignIn — only the ID token is required.
        val credential = GoogleAuthProvider.credential(idToken, null)
        val fbUser = auth.signInWithCredential(credential).user
            ?: return@runCatching AuthResult.Failure("Falha ao autenticar com Google")

        // Gate DEPOIS do signInWithCredential (precisa do email confirmado
        // pelo Google) mas antes de gravar perfil. Se bloquear, desloga
        // do Firebase pra evitar conta Firebase ativa sem autorização.
        // Importante: o `runCatching` externo pega exceção do Firestore
        // e retorna Failure, MAS pula o signOut — então precisamos de um
        // runCatching local pra garantir que a sessão do Firebase Auth é
        // sempre derrubada quando o gate falha ou lança. Sem isso, um erro
        // de rede aqui deixaria o usuário logado em `authStateChanged`, e
        // no próximo reabrir do app ele entraria sem passar pelo gate.
        val resolvedEmail = fbUser.email ?: email
        val gateResult = runCatching { checkEmailAllowed(resolvedEmail) }
            .getOrElse { ex ->
                runCatching { auth.signOut() }
                return@runCatching AuthResult.Failure(
                    ex.message ?: "Erro ao verificar autorização. Tente novamente."
                )
            }
        gateResult?.let { failure ->
            runCatching { auth.signOut() }
            return@runCatching failure
        }

        // First-time Google sign-in: no profile yet. Create a minimal one so
        // the app has something to render; the user can fill matrícula e
        // equipe later from a profile screen (TODO — out of scope for this
        // PR). Email/senha signups already populate those fields in the
        // cadastro form.
        val existing = resolveProfile(fbUser)
        val profile = existing ?: User(
            id = fbUser.uid,
            email = resolvedEmail,
            name = name.ifBlank { resolvedEmail },
            registrationNumber = "",
            team = "",
            shift = Shift.MANHA,
            role = initialRoleFor(resolvedEmail),
            createdAt = Clock.System.now(),
        )

        if (existing == null) {
            runCatching { userRepository.upsert(profile) }
                .onFailure { upsertError ->
                    // Keep the Firebase user signed in (avoids a second Google
                    // prompt loop) but surface the error so the UI can retry
                    // the upsert on the next screen.
                    return@runCatching AuthResult.Failure(
                        upsertError.message ?: "Erro ao salvar perfil. Tente novamente."
                    )
                }
        }

        manualUser.value = profile
        AuthResult.Success(profile)
    }.getOrElse { AuthResult.Failure(it.humanMessage("Falha ao entrar com Google")) }

    override suspend fun completeProfile(
        registrationNumber: String,
        team: String,
        shift: Shift,
    ): AuthResult = runCatching {
        val fbUser = auth.currentUser
            ?: return@runCatching AuthResult.Failure("Nenhum usuário logado")

        // Start from the existing profile so we don't clobber name/email and so
        // `createdAt` stays stable. Fall back to a fresh profile if, for some
        // reason, the user doc doesn't exist yet (e.g. first-run after Google
        // sign-in where the initial upsert failed). No fallback precisamos
        // reaplicar o bootstrap: se o upsert inicial falhou pra um admin
        // bootstrap, não queremos que o "completar cadastro" grave ele como
        // USER e derrube o privilégio.
        val fallbackEmail = fbUser.email ?: ""
        val base = resolveProfile(fbUser) ?: User(
            id = fbUser.uid,
            email = fallbackEmail,
            name = fallbackEmail,
            registrationNumber = "",
            team = "",
            shift = Shift.MANHA,
            role = initialRoleFor(fallbackEmail),
            createdAt = Clock.System.now(),
        )

        val updated = base.copy(
            registrationNumber = registrationNumber,
            team = team,
            shift = shift,
        )
        userRepository.upsert(updated)
        manualUser.value = updated
        AuthResult.Success(updated)
    }.getOrElse { AuthResult.Failure(it.message ?: "Erro ao salvar perfil") }

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
