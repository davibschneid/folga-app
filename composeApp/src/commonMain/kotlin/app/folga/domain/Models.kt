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
    val shift: Shift = Shift.MANHA,
    /**
     * Perfil de acesso. [UserRole.USER] é o default para qualquer novo
     * cadastro. [UserRole.ADMIN] é atribuído automaticamente no primeiro
     * login se o email estiver no bootstrap hardcoded ([AdminBootstrap]),
     * ou manualmente por outro admin na tela de Administração.
     */
    val role: UserRole = UserRole.USER,
    val createdAt: Instant,
)

/**
 * Perfil de acesso do usuário. Admins têm visibilidade total e podem
 * gerenciar outros usuários + a whitelist de e-mails autorizados a entrar
 * no app. Usuários comuns só acessam as próprias folgas/trocas.
 */
enum class UserRole {
    USER,
    ADMIN;

    companion object {
        fun fromString(value: String?): UserRole =
            entries.firstOrNull { it.name == value } ?: USER
    }
}

/**
 * E-mail autorizado a se cadastrar / entrar no app. Gerenciado pela tela
 * de Administração. Ao criar conta (email/senha ou Google), o app checa
 * se o e-mail está nessa lista (ou no [AdminBootstrap]) antes de
 * concluir o cadastro. Sem whitelist, qualquer conta do Google poderia
 * criar perfil no app.
 */
@Serializable
data class AllowedEmail(
    val email: String,
    val addedBy: String,
    val addedAt: Instant,
)

/**
 * Turno de trabalho do colaborador. Usado em "Completar cadastro" (pós login
 * com Google) e no cadastro email/senha. Persistido como enum.name no Firestore
 * (`Shift.name` → String), com fallback pra [Shift.MANHA] em leituras legadas.
 */
enum class Shift {
    MANHA,
    TARDE,
    NOITE;

    companion object {
        fun fromString(value: String?): Shift =
            entries.firstOrNull { it.name == value } ?: MANHA
    }
}

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
