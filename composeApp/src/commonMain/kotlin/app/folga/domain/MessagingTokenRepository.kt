package app.folga.domain

/**
 * Persiste o token FCM (Firebase Cloud Messaging) do dispositivo no doc
 * do usuário em `users/{uid}.fcmToken`. O token é específico do par
 * (app instalado, dispositivo) e muda quando:
 *  - o app é reinstalado;
 *  - os dados do app são limpos;
 *  - o token expira (raro, mas o `FirebaseMessagingService.onNewToken`
 *    avisa).
 *
 * O token é consumido por uma Cloud Function que dispara push ao criar
 * uma `SwapRequest` em PENDING — a função lê esse campo do doc do
 * `targetId` e usa o Admin SDK do Firebase pra enviar a notificação.
 *
 * Implementação faz `update("fcmToken" to token)` em vez de `set` pra
 * não sobrescrever os outros campos do perfil. Se o doc não existe
 * ainda (cenário raro: token chega antes do upsert do perfil), a
 * implementação trata o erro e retorna sem propagar — o sync vai ser
 * tentado de novo na próxima abertura do app.
 */
interface MessagingTokenRepository {
    suspend fun saveToken(userId: String, token: String)

    /**
     * Limpa o token do usuário no momento do logout, pra evitar que o
     * dispositivo continue recebendo push de uma conta deslogada caso
     * outro usuário entre no mesmo aparelho.
     */
    suspend fun clearToken(userId: String)
}

/**
 * Interface para obter o token de registro (FCM) de forma nativa
 * em cada plataforma (Android/iOS).
 */
interface NativeMessagingService {
    suspend fun getRegistrationToken(): String?
}
