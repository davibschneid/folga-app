package app.folga.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.folga.db.FolgaDatabase
import app.folga.domain.User
import app.folga.domain.UserRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlDelightUserRepository(
    private val db: FolgaDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : UserRepository {

    private val queries get() = db.folgaQueries

    override suspend fun upsert(user: User) = withContext(dispatcher) {
        queries.upsertUser(
            id = user.id,
            email = user.email,
            name = user.name,
            registrationNumber = user.registrationNumber,
            team = user.team,
            createdAt = user.createdAt.toEpochMilliseconds(),
        )
    }

    override suspend fun findById(id: String): User? = withContext(dispatcher) {
        queries.selectUserById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun findByEmail(email: String): User? = withContext(dispatcher) {
        queries.selectUserByEmail(email).executeAsOneOrNull()?.toDomain()
    }

    override fun observeAll(): Flow<List<User>> =
        queries.selectAllUsers().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }
}
