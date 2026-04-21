package app.folga.data

import app.folga.domain.User
import app.folga.domain.UserRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
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
        users.document(user.id).set(user.toDto())
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

    override fun observeAll(): Flow<List<User>> =
        users.snapshots.map { snap ->
            snap.documents.map { it.data(UserDto.serializer()).toDomain() }
        }

    private companion object {
        const val COLLECTION = "users"
    }
}
