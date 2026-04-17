package app.folga.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.folga.db.FolgaDatabase
import app.folga.domain.FolgaStatus
import app.folga.domain.SwapRequest
import app.folga.domain.SwapRepository
import app.folga.domain.SwapStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlin.random.Random

class SqlDelightSwapRepository(
    private val db: FolgaDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SwapRepository {

    private val queries get() = db.folgaQueries

    override suspend fun request(
        fromFolgaId: String,
        toFolgaId: String,
        requesterId: String,
        targetId: String,
        message: String?,
    ): SwapRequest = withContext(dispatcher) {
        val now = Clock.System.now()
        val swap = SwapRequest(
            id = randomId(),
            fromFolgaId = fromFolgaId,
            toFolgaId = toFolgaId,
            requesterId = requesterId,
            targetId = targetId,
            status = SwapStatus.PENDING,
            message = message,
            createdAt = now,
            respondedAt = null,
        )
        queries.upsertSwap(
            id = swap.id,
            fromFolgaId = swap.fromFolgaId,
            toFolgaId = swap.toFolgaId,
            requesterId = swap.requesterId,
            targetId = swap.targetId,
            status = swap.status.name,
            message = swap.message,
            createdAt = swap.createdAt.toEpochMilliseconds(),
            respondedAt = null,
        )
        swap
    }

    override suspend fun accept(swapId: String) = withContext(dispatcher) {
        val swap = queries.selectSwapById(swapId).executeAsOneOrNull() ?: return@withContext
        val fromFolga = queries.selectFolgaById(swap.fromFolgaId).executeAsOneOrNull()
        val toFolga = queries.selectFolgaById(swap.toFolgaId).executeAsOneOrNull()
        if (fromFolga != null && toFolga != null) {
            // Swap the owners (who the folga belongs to) keeping the dates
            queries.upsertFolga(
                id = fromFolga.id,
                userId = toFolga.userId,
                date = fromFolga.date,
                status = FolgaStatus.SWAPPED.name,
                note = fromFolga.note,
                createdAt = fromFolga.createdAt,
            )
            queries.upsertFolga(
                id = toFolga.id,
                userId = fromFolga.userId,
                date = toFolga.date,
                status = FolgaStatus.SWAPPED.name,
                note = toFolga.note,
                createdAt = toFolga.createdAt,
            )
        }
        queries.upsertSwap(
            id = swap.id,
            fromFolgaId = swap.fromFolgaId,
            toFolgaId = swap.toFolgaId,
            requesterId = swap.requesterId,
            targetId = swap.targetId,
            status = SwapStatus.ACCEPTED.name,
            message = swap.message,
            createdAt = swap.createdAt,
            respondedAt = Clock.System.now().toEpochMilliseconds(),
        )
    }

    override suspend fun reject(swapId: String) = updateStatus(swapId, SwapStatus.REJECTED)

    override suspend fun cancel(swapId: String) = updateStatus(swapId, SwapStatus.CANCELLED)

    private suspend fun updateStatus(swapId: String, newStatus: SwapStatus) = withContext(dispatcher) {
        val swap = queries.selectSwapById(swapId).executeAsOneOrNull() ?: return@withContext
        queries.upsertSwap(
            id = swap.id,
            fromFolgaId = swap.fromFolgaId,
            toFolgaId = swap.toFolgaId,
            requesterId = swap.requesterId,
            targetId = swap.targetId,
            status = newStatus.name,
            message = swap.message,
            createdAt = swap.createdAt,
            respondedAt = Clock.System.now().toEpochMilliseconds(),
        )
    }

    override fun observeIncoming(userId: String): Flow<List<SwapRequest>> =
        queries.selectSwapsByTarget(userId).asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeOutgoing(userId: String): Flow<List<SwapRequest>> =
        queries.selectSwapsByRequester(userId).asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    private fun randomId(): String = buildString {
        repeat(16) {
            val c = Random.nextInt(36)
            append(if (c < 10) ('0'.code + c).toChar() else ('a'.code + (c - 10)).toChar())
        }
    }
}
