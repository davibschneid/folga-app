package app.folga.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.folga.domain.MessagingTokenRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import kotlin.random.Random

/**
 * Recebe os pushes do Firebase Cloud Messaging e cuida do ciclo de vida
 * do token FCM:
 *
 * - [onNewToken]: o servidor do FCM rotacionou o token (raro, mas
 *   acontece após reinstalação ou limpeza de dados). Salvamos no doc
 *   `users/{uid}` se houver usuário logado — se não houver, o app vai
 *   pegar o token atual via `FirebaseMessaging.getInstance().token` no
 *   próximo login (ver [FcmTokenSyncer]).
 * - [onMessageReceived]: chega quando o app está em foreground OU quando
 *   o servidor manda payload tipo `data` (sem `notification`). Como o
 *   Cloud Function envia `notification` + `data`, o sistema já mostra a
 *   tray notification em background sozinho — esse callback aqui cobre
 *   só o caso de foreground (pra não perder a notificação na tela).
 */
class FolgaMessagingService : FirebaseMessagingService() {

    // Koin já tá inicializado pelo FolgaApplication (Application é
    // criado antes de qualquer Service). Inject lazy pra não quebrar
    // se o ciclo de vida do Service for chamado em condições estranhas.
    private val messagingTokenRepository: MessagingTokenRepository by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            messagingTokenRepository.saveToken(uid, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Easy Folgas"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: "Você tem uma notificação"
        showNotification(this, title, body)
    }

    companion object {
        const val CHANNEL_ID = "swap_requests"
        const val CHANNEL_NAME = "Solicitações de troca"
        const val CHANNEL_DESCRIPTION =
            "Notifica quando alguém pede pra trocar um dia de trabalho com você."

        /**
         * Cria o canal de notificação exigido a partir do Android 8.
         * Idempotente — chamar de novo só atualiza nome/descrição.
         * Chamado pelo `FolgaApplication.onCreate` pra garantir que o
         * canal existe antes de qualquer push chegar.
         */
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = CHANNEL_DESCRIPTION
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        private fun showNotification(context: Context, title: String, body: String) {
            ensureChannel(context)
            // PendingIntent abre o app na tela Trocas (atual launcher) ao
            // tocar a notificação. Pra simplificar, abre o launcher
            // padrão — a tela inicial já joga o usuário no Home, e o
            // badge do bottom bar destaca a aba Trocas.
            val launchIntent = context.packageManager
                .getLaunchIntentForPackage(context.packageName)
            val pendingIntent = launchIntent?.let {
                PendingIntent.getActivity(
                    context,
                    0,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                // `ic_dialog_info` é o único ícone do framework garantido
                // em todos os SDKs sem ter que adicionar drawable nosso.
                // Quando tivermos um ícone próprio em `res/drawable`, é
                // só apontar pra ele.
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .apply { pendingIntent?.let(::setContentIntent) }
                .build()

            // ID aleatório pra notificações empilharem em vez de
            // sobrescrever — se chegam duas solicitações de troca em
            // sequência, o usuário vê as duas.
            val notificationId = Random.nextInt()
            val nm = NotificationManagerCompat.from(context)
            // Em API 33+ é preciso permissão POST_NOTIFICATIONS — se o
            // usuário negou, `notify` simplesmente não exibe (não lança).
            runCatching { nm.notify(notificationId, notification) }
        }
    }
}
