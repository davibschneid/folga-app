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
        // runTransaction reads + writes atomically under optimistic
        // concurrency: if any of the three docs mutate between the get()s
        // and the commit, Firestore aborts and re-runs the block. That's
        // what keeps two concurrent accept()s (or an accept racing a
        // folga cancel) from corrupting ownership.
        firestore.runTransaction {
            val swapSnap = get(swapRef)
            if (!swapSnap.exists) return@runTransaction
            val swap = swapSnap.data(SwapDto.serializer())

            // Only a PENDING swap is acceptable. Without this guard a
            // concurrent accept() that already committed would trigger
            // a retry which re-reads the post-swap folgas and then
            // writes them back *un*-swapped, reverting ownership.
            if (swap.status != SwapStatus.PENDING.name) return@runTransaction

            val fromRef = folgas.document(swap.fromFolgaId)
            val toRef = folgas.document(swap.toFolgaId)
            val fromSnap = get(fromRef)
            val toSnap = get(toRef)
            if (!fromSnap.exists || !toSnap.exists) return@runTransaction
            val from = fromSnap.data(FolgaDto.serializer())
            val to = toSnap.data(FolgaDto.serializer())
            // Don't resurrect a CANCELLED / SWAPPED folga. Only folgas
            // still SCHEDULED are valid targets for the ownership swap —
            // otherwise a user who cancelled before the other side hit
            // accept would see their folga flip back to SWAPPED.
            if (from.status != FolgaStatus.SCHEDULED.name ||
                to.status != FolgaStatus.SCHEDULED.name) return@runTransaction

            set(
                documentRef = fromRef,
                strategy = FolgaDto.serializer(),
                data = from.copy(userId = to.userId, status = FolgaStatus.SWAPPED.name),
            )
            set(
                documentRef = toRef,
                strategy = FolgaDto.serializer(),
                data = to.copy(userId = from.userId, status = FolgaStatus.SWAPPED.name),
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

    private companion object {
        const val SWAPS = "swaps"
        const val FOLGAS = "folgas"
    }
}
