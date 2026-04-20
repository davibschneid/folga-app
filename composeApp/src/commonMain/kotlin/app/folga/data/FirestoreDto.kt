package app.folga.data

import app.folga.domain.Folga
import app.folga.domain.FolgaStatus
import app.folga.domain.SwapRequest
import app.folga.domain.SwapStatus
import app.folga.domain.User
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
    val createdAt: Long = 0L,
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
    createdAt = createdAt.toEpochMilliseconds(),
)

internal fun UserDto.toDomain(): User = User(
    id = id,
    email = email,
    name = name,
    registrationNumber = registrationNumber,
    team = team,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
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
    toFolgaId = toFolgaId,
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
    toFolgaId = toFolgaId,
    requesterId = requesterId,
    targetId = targetId,
    status = SwapStatus.fromString(status),
    message = message,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    respondedAt = respondedAt?.let { Instant.fromEpochMilliseconds(it) },
)
