package app.folga.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val email: String,
    val name: String,
    val registrationNumber: String,
    val team: String,
    val createdAt: Instant,
)

enum class FolgaStatus {
    SCHEDULED,
    COMPLETED,
    SWAPPED,
    CANCELLED;

    companion object {
        fun fromString(value: String): FolgaStatus = entries.firstOrNull { it.name == value } ?: SCHEDULED
    }
}

@Serializable
data class Folga(
    val id: String,
    val userId: String,
    val date: LocalDate,
    val status: FolgaStatus,
    val note: String?,
    val createdAt: Instant,
)

enum class SwapStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED;

    companion object {
        fun fromString(value: String): SwapStatus = entries.firstOrNull { it.name == value } ?: PENDING
    }
}

@Serializable
data class SwapRequest(
    val id: String,
    val fromFolgaId: String,
    val toFolgaId: String,
    val requesterId: String,
    val targetId: String,
    val status: SwapStatus,
    val message: String?,
    val createdAt: Instant,
    val respondedAt: Instant?,
)
