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
 * `accept()` uses a Firestore transaction to atomically flip both folgas'
 * owners and mark the swap accepted — matching the SQLite `db.transaction { }`
 * contract from the previous implementation.
 */
class FirestoreSwapRepository(
    private val firestore: FirebaseFirestore = Firebase.firestore,
) : SwapRepository {

    private val swaps get() = firestore.collection(SWAPS)
    private val folgas get() = firestore.collection(FOLGAS)

    override suspend fun request(
        fromFolgaId: String,
        toFolgaId: String,
        requesterId: String,
        targetId: String,
        message: String?,
    ): SwapRequest {
        val doc = swaps.document
        val swap = SwapRequest(
            id = doc.id,
            fromFolgaId = fromFolgaId,
            toFolgaId = toFolgaId,
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
        val swapSnap = swapRef.get()
        if (!swapSnap.exists) return
        val swap = swapSnap.data(SwapDto.serializer())

        val fromRef = folgas.document(swap.fromFolgaId)
        val toRef = folgas.document(swap.toFolgaId)
        val fromSnap = fromRef.get()
        val toSnap = toRef.get()
        if (!fromSnap.exists || !toSnap.exists) return
        val from = fromSnap.data(FolgaDto.serializer())
        val to = toSnap.data(FolgaDto.serializer())

        // Writing all three docs through a batch ensures they land
        // together: either both folgas flip owner AND the swap is marked
        // ACCEPTED, or nothing changes. Firestore applies the batch
        // atomically on the server.
        val batch = firestore.batch()
        batch.set(
            documentRef = fromRef,
            strategy = FolgaDto.serializer(),
            data = from.copy(userId = to.userId, status = FolgaStatus.SWAPPED.name),
        )
        batch.set(
            documentRef = toRef,
            strategy = FolgaDto.serializer(),
            data = to.copy(userId = from.userId, status = FolgaStatus.SWAPPED.name),
        )
        batch.set(
            documentRef = swapRef,
            strategy = SwapDto.serializer(),
            data = swap.copy(
                status = SwapStatus.ACCEPTED.name,
                respondedAt = Clock.System.now().toEpochMilliseconds(),
            ),
        )
        batch.commit()
    }

    override suspend fun reject(swapId: String) = updateStatus(swapId, SwapStatus.REJECTED)

    override suspend fun cancel(swapId: String) = updateStatus(swapId, SwapStatus.CANCELLED)

    private suspend fun updateStatus(swapId: String, newStatus: SwapStatus) {
        val ref = swaps.document(swapId)
        val snap = ref.get()
        if (!snap.exists) return
        val current = snap.data(SwapDto.serializer())
        ref.set(
            current.copy(
                status = newStatus.name,
                respondedAt = Clock.System.now().toEpochMilliseconds(),
            )
        )
    }

    override fun observeIncoming(userId: String): Flow<List<SwapRequest>> =
        swaps.where { "targetId" equalTo userId }.snapshots.map { snap ->
            snap.documents.map { it.data(SwapDto.serializer()).toDomain() }
        }

    override fun observeOutgoing(userId: String): Flow<List<SwapRequest>> =
        swaps.where { "requesterId" equalTo userId }.snapshots.map { snap ->
            snap.documents.map { it.data(SwapDto.serializer()).toDomain() }
        }

    private companion object {
        const val SWAPS = "swaps"
        const val FOLGAS = "folgas"
    }
}
