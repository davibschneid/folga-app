package app.folga.data

import app.folga.domain.MessagingTokenRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore

/**
 * Implementação do [MessagingTokenRepository] que escreve o token FCM
 * direto no doc `users/{uid}` via `update("fcmToken" to token)`.
 *
 * Usa `update` em vez de `set` pra não tocar nos outros campos do
 * perfil — assim conseguimos sincronizar token sem ter que carregar
 * o User completo só pra reescrever igual.
 *
 * O `runCatching` engole erros (rede, regra negada, doc não existe
 * ainda) porque o sync de token é "best effort": rodamos toda vez
 * que o app abre logado, então uma falha pontual aqui não impede
 * push numa próxima sessão. Logamos como warning mas não propagamos.
 */
class FirestoreMessagingTokenRepository(
    private val firestore: FirebaseFirestore = Firebase.firestore,
) : MessagingTokenRepository {

    private val users get() = firestore.collection("users")

    override suspend fun saveToken(userId: String, token: String) {
        runCatching {
            users.document(userId).update("fcmToken" to token)
        }
    }

    override suspend fun clearToken(userId: String) {
        runCatching {
            users.document(userId).update("fcmToken" to null)
        }
    }
}
