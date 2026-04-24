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
    /**
     * URL pública de download da foto de perfil no Firebase Storage.
     * `null` quando o usuário ainda não subiu foto — a UI cai pro
     * fallback de iniciais (`ProfileAvatar`). O upload é feito pelo
     * [PhotoStorageRepository] e a URL é persistida no doc do usuário
     * via [AuthRepository.updatePhotoUrl].
     */
    val photoUrl: String? = null,
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

    /**
     * Grupo de turno para fins de troca. Regra do cliente: só podem
     * trocar entre si colegas do mesmo grupo — os diurnos (MANHA/TARDE)
     * são intercambiáveis, os noturnos (NOITE) só trocam com noturnos.
     */
    val group: ShiftGroup
        get() = when (this) {
            MANHA, TARDE -> ShiftGroup.DIURNO
            NOITE -> ShiftGroup.NOTURNO
        }

    /**
     * Indica se duas pessoas com esses turnos podem trocar entre si.
     * Simétrica — `a.isCompatibleWith(b) == b.isCompatibleWith(a)`.
     */
    fun isCompatibleWith(other: Shift): Boolean = this.group == other.group

    companion object {
        fun fromString(value: String?): Shift =
            entries.firstOrNull { it.name == value } ?: MANHA
    }
}

/**
 * Agrupamento de turnos para a regra de compatibilidade de troca:
 * manhã e tarde compartilham o grupo DIURNO (podem trocar entre si),
 * noite fica no grupo NOTURNO (só troca com noite).
 */
enum class ShiftGroup {
    DIURNO,
    NOTURNO,
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

/**
 * Pedido de troca unidirecional. O requester cadastrou um dia de trabalho
 * ([fromFolgaId]) e está pedindo que [targetId] assuma esse dia. Se o
 * target aceitar, o dia passa pra ele (ownership do [Folga] muda + status
 * vira [FolgaStatus.SWAPPED]); o requester fica sem esse dia de trabalho.
 *
 * Modelo antigo (pré-PR de fluxo unidirecional) tinha também um campo
 * `toFolgaId` com o dia do colega pra trocar em contrapartida. Esse campo
 * foi removido do domínio — no Firestore ainda fica presente em docs
 * antigos por compatibilidade, mas é ignorado no toDomain().
 */
@Serializable
data class SwapRequest(
    val id: String,
    val fromFolgaId: String,
    val requesterId: String,
    val targetId: String,
    val status: SwapStatus,
    val message: String?,
    val createdAt: Instant,
    val respondedAt: Instant?,
)
