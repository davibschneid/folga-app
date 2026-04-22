package app.folga.data

import app.folga.domain.AllowedEmail
import app.folga.domain.Folga
import app.folga.domain.FolgaStatus
import app.folga.domain.Shift
import app.folga.domain.SwapRequest
import app.folga.domain.SwapStatus
import app.folga.domain.User
import app.folga.domain.UserRole
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Flat @Serializable DTOs used as the on-the-wire shape in Firestore. Kept
 * separate from the domain models so `Instant`/`LocalDate`/enums can be
 * mapped to Firestore-friendly primitives (Long epoch millis and String ISO
 * dates) without bleeding `kotlinx.datetime` into the schema.
 */

@Serializable
internal data class UserDto(
    val id: String = "",
    val email: String = "",
    val name: String = "",
    val registrationNumber: String = "",
    val team: String = "",
    // Nullable + defaulted so docs created before PR #8 (pré-turno) keep
    // deserializing. `Shift.fromString` falls back to MANHA on unknown/missing.
    val shift: String? = null,
    // Nullable + defaulted para docs criados antes do PR #10. `UserRole.fromString`
    // cai para USER em qualquer valor desconhecido/ausente, mantendo o
    // princípio do menor privilégio: se um doc perdeu o campo, o usuário
    // vira um usuário comum até ser repromovido.
    val role: String? = null,
    val createdAt: Long = 0L,
)

@Serializable
internal data class AllowedEmailDto(
    val email: String = "",
    val addedBy: String = "",
    val addedAt: Long = 0L,
)

@Serializable
internal data class FolgaDto(
    val id: String = "",
    val userId: String = "",
    val date: String = "",
    val status: String = FolgaStatus.SCHEDULED.name,
    val note: String? = null,
    val createdAt: Long = 0L,
)

@Serializable
internal data class SwapDto(
    val id: String = "",
    val fromFolgaId: String = "",
    // `toFolgaId` foi mantido no DTO pra continuar lendo docs antigos
    // (modelo bidirecional pré-PR do fluxo unidirecional). Default vazio
    // para novos docs — a regra de Firestore não valida mais imutabilidade
    // desse campo. Ignorado no toDomain().
    val toFolgaId: String = "",
    val requesterId: String = "",
    val targetId: String = "",
    val status: String = SwapStatus.PENDING.name,
    val message: String? = null,
    val createdAt: Long = 0L,
    val respondedAt: Long? = null,
)

internal fun User.toDto(): UserDto = UserDto(
    id = id,
    email = email,
    name = name,
    registrationNumber = registrationNumber,
    team = team,
    shift = shift.name,
    role = role.name,
    createdAt = createdAt.toEpochMilliseconds(),
)

internal fun UserDto.toDomain(): User = User(
    id = id,
    email = email,
    name = name,
    registrationNumber = registrationNumber,
    team = team,
    shift = Shift.fromString(shift),
    role = UserRole.fromString(role),
    createdAt = Instant.fromEpochMilliseconds(createdAt),
)

internal fun AllowedEmail.toDto(): AllowedEmailDto = AllowedEmailDto(
    email = email,
    addedBy = addedBy,
    addedAt = addedAt.toEpochMilliseconds(),
)

internal fun AllowedEmailDto.toDomain(): AllowedEmail = AllowedEmail(
    email = email,
    addedBy = addedBy,
    addedAt = Instant.fromEpochMilliseconds(addedAt),
)

internal fun Folga.toDto(): FolgaDto = FolgaDto(
    id = id,
    userId = userId,
    date = date.toString(),
    status = status.name,
    note = note,
    createdAt = createdAt.toEpochMilliseconds(),
)

internal fun FolgaDto.toDomain(): Folga = Folga(
    id = id,
    userId = userId,
    date = LocalDate.parse(date),
    status = FolgaStatus.fromString(status),
    note = note,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
)

internal fun SwapRequest.toDto(): SwapDto = SwapDto(
    id = id,
    fromFolgaId = fromFolgaId,
    // `toFolgaId` saiu do domínio no modelo unidirecional. Escrevemos ""
    // pra manter a mesma shape do doc e pra conviver com docs antigos
    // no mesmo collection.
    toFolgaId = "",
    requesterId = requesterId,
    targetId = targetId,
    status = status.name,
    message = message,
    createdAt = createdAt.toEpochMilliseconds(),
    respondedAt = respondedAt?.toEpochMilliseconds(),
)

internal fun SwapDto.toDomain(): SwapRequest = SwapRequest(
    id = id,
    fromFolgaId = fromFolgaId,
    // `toFolgaId` existe no DTO só pra não quebrar a leitura de docs
    // antigos; é ignorado aqui no modelo unidirecional.
    requesterId = requesterId,
    targetId = targetId,
    status = SwapStatus.fromString(status),
    message = message,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    respondedAt = respondedAt?.let { Instant.fromEpochMilliseconds(it) },
)
