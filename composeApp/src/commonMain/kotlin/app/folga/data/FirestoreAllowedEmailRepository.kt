package app.folga.data

import app.folga.domain.AdminBootstrap
import app.folga.domain.AllowedEmail
import app.folga.domain.AllowedEmailRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

/**
 * Whitelist de e-mails no Firestore. Usamos o próprio e-mail (normalizado
 * com [AdminBootstrap.normalize] — trim + lowercase) como docId pra permitir
 * checagem O(1) no gate de signup/signin (apenas um `get` por e-mail, sem
 * precisar de query com `where`). O índice padrão cuida do resto.
 *
 * Os admins bootstrap são sempre permitidos ainda que a collection esteja
 * vazia — a checagem [AdminBootstrap.isBootstrapAdmin] acontece antes da
 * consulta ao Firestore no `FirebaseAuthRepository`.
 */
class FirestoreAllowedEmailRepository(
    private val firestore: FirebaseFirestore = Firebase.firestore,
) : AllowedEmailRepository {

    private val collection get() = firestore.collection(COLLECTION)

    override suspend fun isAllowed(email: String): Boolean {
        val normalized = AdminBootstrap.normalize(email)
        if (normalized.isEmpty()) return false
        if (normalized in AdminBootstrap.ADMIN_EMAILS) return true
        return collection.document(normalized).get().exists
    }

    override suspend fun add(email: String, addedBy: String) {
        val normalized = AdminBootstrap.normalize(email)
        require(normalized.isNotEmpty()) { "E-mail vazio" }
        val dto = AllowedEmail(
            email = normalized,
            addedBy = addedBy,
            addedAt = Clock.System.now(),
        ).toDto()
        collection.document(normalized).set(dto)
    }

    override suspend fun remove(email: String) {
        val normalized = AdminBootstrap.normalize(email)
        if (normalized.isEmpty()) return
        // Não deixa apagar os admins bootstrap pela UI — eles sempre têm
        // acesso via `isBootstrapAdmin`. Apagar só geraria confusão
        // (o doc some, mas o admin continua entrando).
        if (normalized in AdminBootstrap.ADMIN_EMAILS) return
        collection.document(normalized).delete()
    }

    // `catch { emit(emptyList()) }` mesma motivação dos outros repos
    // (ver FirestoreFolgaRepository): se o admin tá na tela de
    // Administração e clica Sair, esse listener pode receber
    // PERMISSION_DENIED no snapshot final (rule de `allowed_emails`
    // exige `isAdmin()` pra `list`). Sem catch, a exceção crashava o
    // app pelo `stateIn(viewModelScope)` do AdminViewModel.
    override fun observeAll(): Flow<List<AllowedEmail>> =
        collection.snapshots.map { snap ->
            snap.documents
                .map { it.data(AllowedEmailDto.serializer()).toDomain() }
                .sortedBy { it.email }
        }.catch { emit(emptyList()) }

    private companion object {
        const val COLLECTION = "allowed_emails"
    }
}
