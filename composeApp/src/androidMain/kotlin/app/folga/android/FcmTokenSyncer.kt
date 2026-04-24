package app.folga.android

import android.util.Log
import app.folga.domain.AuthRepository
import app.folga.domain.MessagingTokenRepository
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Sincroniza o token FCM com o doc do usuário logado.
 *
 * Fluxo:
 *  - assina o `authRepository.currentUser`. Toda vez que esse fluxo
 *    emite um `User` não-nulo (login bem-sucedido OU app reabrindo já
 *    logado), pega o token atual via `FirebaseMessaging.getInstance().token`
 *    e salva em `users/{uid}.fcmToken`.
 *
 * Por que sincronizar aqui em vez de só no `FirebaseMessagingService.onNewToken`?
 *  - `onNewToken` só dispara quando o token rotaciona. Em apps já
 *    instalados há tempo, ele pode não disparar nunca após o login —
 *    então a Cloud Function nunca acharia o token desse usuário.
 *  - Pegando o token na hora do login (mesmo que igual ao anterior),
 *    garantimos que o doc do usuário está sempre com o token mais
 *    recente do dispositivo onde ele está logado agora.
 */
class FcmTokenSyncer(
    private val authRepository: AuthRepository,
    private val messagingTokenRepository: MessagingTokenRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        scope.launch {
            authRepository.currentUser.filterNotNull().collect { user ->
                fetchAndSave(user.id)
            }
        }
    }

    private fun fetchAndSave(userId: String) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener(
            OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "Falha ao obter token FCM", task.exception)
                    return@OnCompleteListener
                }
                val token = task.result ?: return@OnCompleteListener
                scope.launch {
                    messagingTokenRepository.saveToken(userId, token)
                }
            },
        )
    }

    private companion object {
        const val TAG = "FcmTokenSyncer"
    }
}
