package app.folga.data

import app.folga.domain.FolgaStatus
import app.folga.domain.SwapRepository
import app.folga.domain.SwapRequest
import app.folga.domain.SwapStatus
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

/**
 * Firestore-backed [SwapRepository]. Swap requests live under `swaps/{id}`.
 * `accept()` uses `firestore.runTransaction { }` so reads of the swap and both
 * folgas, plus the three writes that flip owners and mark the swap accepted,
 * all run under Firestore's optimistic concurrency control. This matches the
 * `db.transaction { }` contract from the SQLite implementation and prevents
 * two clients from racing to accept the same swap (or concurrently cancelling
 * a folga that is about to be flipped).
 */
class FirestoreSwapRepository(
    private val firestore: FirebaseFirestore = Firebase.firestore,
) : SwapRepository {

    private val swaps get() = firestore.collection(SWAPS)
    private val folgas get() = firestore.collection(FOLGAS)

    override suspend fun request(
        fromFolgaId: String,
        requesterId: String,
        targetId: String,
        message: String?,
    ): SwapRequest {
        val doc = swaps.document
        val swap = SwapRequest(
            id = doc.id,
            fromFolgaId = fromFolgaId,
            requesterId = requesterId,
            targetId = targetId,
            status = SwapStatus.PENDING,
            message = message,
            createdAt = Clock.System.now(),
            respondedAt = null,
        )
        doc.set(swap.toDto())
        return swap
    }

    override suspend fun accept(swapId: String) {
        val swapRef = swaps.document(swapId)
        // runTransaction: leituras + escritas atômicas com optimistic
        // concurrency. Se o swap ou a folga mudarem entre o get() e o
        // commit, Firestore aborta e refaz o bloco — é isso que impede
        // dois accept() concorrentes (ou um accept racing com o cancel
        // da folga) de corromperem a ownership.
        firestore.runTransaction {
            val swapSnap = get(swapRef)
            if (!swapSnap.exists) return@runTransaction
            val swap = swapSnap.data(SwapDto.serializer())

            // Só aceita swaps PENDING. Sem esse guard, um accept() que já
            // commitou dispararia retry, releria a folga pós-flip e
            // reescreveria de volta desfazendo a transferência.
            if (swap.status != SwapStatus.PENDING.name) return@runTransaction

            val fromRef = folgas.document(swap.fromFolgaId)
            val fromSnap = get(fromRef)
            if (!fromSnap.exists) return@runTransaction
            val from = fromSnap.data(FolgaDto.serializer())
            // Só aceita transferir folga ainda SCHEDULED. Se o requester
            // cancelou antes do target aceitar, a folga já virou CANCELLED
            // — não queremos ressurgir como SWAPPED.
            if (from.status != FolgaStatus.SCHEDULED.name) return@runTransaction

            // Fluxo unidirecional: o target assume o dia. Ownership da
            // folga muda pro target e status vira SWAPPED pra sair da
            // lista "Meus dias cadastrados" do requester. Ambos continuam
            // vendo o compromisso na seção "Trocas agendadas" da home.
            set(
                documentRef = fromRef,
                strategy = FolgaDto.serializer(),
                data = from.copy(
                    userId = swap.targetId,
                    status = FolgaStatus.SWAPPED.name,
                ),
            )
            set(
                documentRef = swapRef,
                strategy = SwapDto.serializer(),
                data = swap.copy(
                    status = SwapStatus.ACCEPTED.name,
                    respondedAt = Clock.System.now().toEpochMilliseconds(),
                ),
            )
        }
    }

    override suspend fun reject(swapId: String) = resolvePending(swapId, SwapStatus.REJECTED)

    override suspend fun cancel(swapId: String) = resolvePending(swapId, SwapStatus.CANCELLED)

    /**
     * Transactionally transitions a swap out of PENDING into [newStatus]
     * (REJECTED or CANCELLED). The PENDING guard prevents a late reject /
     * cancel from overwriting a swap that a concurrent [accept] already
     * applied — otherwise the two folgas would stay swapped on the server
     * but the swap record would show REJECTED/CANCELLED.
     */
    private suspend fun resolvePending(swapId: String, newStatus: SwapStatus) {
        val ref = swaps.document(swapId)
        firestore.runTransaction {
            val snap = get(ref)
            if (!snap.exists) return@runTransaction
            val current = snap.data(SwapDto.serializer())
            if (current.status != SwapStatus.PENDING.name) return@runTransaction
            set(
                documentRef = ref,
                strategy = SwapDto.serializer(),
                data = current.copy(
                    status = newStatus.name,
                    respondedAt = Clock.System.now().toEpochMilliseconds(),
                ),
            )
        }
    }

    override fun observeIncoming(userId: String): Flow<List<SwapRequest>> =
        swaps.where { "targetId" equalTo userId }.snapshots.map { snap ->
            snap.documents.map { it.data(SwapDto.serializer()).toDomain() }
        }

    override fun observeOutgoing(userId: String): Flow<List<SwapRequest>> =
        swaps.where { "requesterId" equalTo userId }.snapshots.map { snap ->
            snap.documents.map { it.data(SwapDto.serializer()).toDomain() }
        }

    override fun observeAll(): Flow<List<SwapRequest>> =
        swaps.snapshots.map { snap ->
            snap.documents.map { it.data(SwapDto.serializer()).toDomain() }
        }

    private companion object {
        const val SWAPS = "swaps"
        const val FOLGAS = "folgas"
    }
}
