package app.folga.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.folga.db.FolgaDatabase
import app.folga.domain.Folga
import app.folga.domain.FolgaRepository
import app.folga.domain.FolgaStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlin.random.Random

class SqlDelightFolgaRepository(
    private val db: FolgaDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : FolgaRepository {

    private val queries get() = db.folgaQueries

    override suspend fun reserve(userId: String, date: LocalDate, note: String?): Folga =
        withContext(dispatcher) {
            val folga = Folga(
                id = randomId(),
                userId = userId,
                date = date,
                status = FolgaStatus.SCHEDULED,
                note = note,
                createdAt = Clock.System.now(),
            )
            queries.upsertFolga(
                id = folga.id,
                userId = folga.userId,
                date = folga.date.toString(),
                status = folga.status.name,
                note = folga.note,
                createdAt = folga.createdAt.toEpochMilliseconds(),
            )
            folga
        }

    override suspend fun cancel(id: String) = withContext(dispatcher) {
        val existing = queries.selectFolgaById(id).executeAsOneOrNull() ?: return@withContext
        queries.upsertFolga(
            id = existing.id,
            userId = existing.userId,
            date = existing.date,
            status = FolgaStatus.CANCELLED.name,
            note = existing.note,
            createdAt = existing.createdAt,
        )
    }

    override suspend fun findById(id: String): Folga? = withContext(dispatcher) {
        queries.selectFolgaById(id).executeAsOneOrNull()?.toDomain()
    }

    override fun observeByUser(userId: String): Flow<List<Folga>> =
        queries.selectFolgasByUser(userId).asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeAll(): Flow<List<Folga>> =
        queries.selectAllFolgas().asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    private fun randomId(): String = buildString {
        repeat(16) {
            val c = Random.nextInt(36)
            append(if (c < 10) ('0'.code + c).toChar() else ('a'.code + (c - 10)).toChar())
        }
    }
}
