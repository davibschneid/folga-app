package app.folga.data

import app.folga.db.FolgaEntity
import app.folga.db.SwapRequestEntity
import app.folga.db.UserEntity
import app.folga.domain.Folga
import app.folga.domain.FolgaStatus
import app.folga.domain.SwapRequest
import app.folga.domain.SwapStatus
import app.folga.domain.User
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

internal fun UserEntity.toDomain(): User = User(
    id = id,
    email = email,
    name = name,
    registrationNumber = registrationNumber,
    team = team,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
)

internal fun FolgaEntity.toDomain(): Folga = Folga(
    id = id,
    userId = userId,
    date = LocalDate.parse(date),
    status = FolgaStatus.fromString(status),
    note = note,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
)

internal fun SwapRequestEntity.toDomain(): SwapRequest = SwapRequest(
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
