package app.folga.data

import app.folga.domain.User
import app.folga.domain.UserRepository
import app.folga.domain.UserRole
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Stores user profiles in Firestore under `users/{uid}`. The document id is
 * always the Firebase Auth uid, which `FirebaseAuthRepository` supplies on
 * sign-up. This means profiles are shared across every device the user signs
 * in from, fixing the "Perfil não encontrado" failure mode we had while the
 * profile lived in SQLite.
 */
class FirestoreUserRepository(
    private val firestore: FirebaseFirestore = Firebase.firestore,
) : UserRepository {

    private val users get() = firestore.collection(COLLECTION)

    override suspend fun upsert(user: User) {
        // `merge = true` preserva campos fora do [UserDto] — hoje é o
        // `fcmToken` gravado pelo FcmTokenSyncer fora desse schema. Sem
        // merge, qualquer upsert (editar perfil, completar cadastro,
        // trocar foto) apagaria o token e deixaria a Cloud Function
        // sem como mirar o device até o próximo reabrir do app.
        users.document(user.id).set(user.toDto(), merge = true)
    }

    override suspend fun findById(id: String): User? {
        val snapshot = users.document(id).get()
        if (!snapshot.exists) return null
        return snapshot.data(UserDto.serializer()).toDomain()
    }

    override suspend fun findByEmail(email: String): User? {
        val query = users.where { "email" equalTo email }.get()
        val doc = query.documents.firstOrNull() ?: return null
        return doc.data(UserDto.serializer()).toDomain()
    }

    override suspend fun updateRole(userId: String, role: UserRole) {
        // `update()` over `set(merge)` pra não apagar campos inexistentes no
        // DTO — ex.: docs antigos sem `role` continuam válidos, só atualiza
        // esse campo específico.
        users.document(userId).update("role" to role.name)
    }

    // `catch { emit(emptyList()) }` evita crash do app no logout: depois
    // de `auth.signOut()` o listener fica ativo até o `stateIn` desistir,
    // e o snapshot seguinte vem com PERMISSION_DENIED (rules exigem
    // `isSignedIn()`). Sem esse catch, a exceção subia pelos ViewModels
    // e crashava o processo.
    override fun observeAll(): Flow<List<User>> =
        users.snapshots.map { snap ->
            snap.documents.map { it.data(UserDto.serializer()).toDomain() }
        }.catch { emit(emptyList()) }

    private companion object {
        const val COLLECTION = "users"
    }
}
