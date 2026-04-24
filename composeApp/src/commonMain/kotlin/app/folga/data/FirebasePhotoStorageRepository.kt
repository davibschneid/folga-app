package app.folga.data

import app.folga.domain.PhotoStorageRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.storage.Data
import dev.gitlive.firebase.storage.storage

/**
 * Upload de foto de perfil pro Firebase Storage via GitLive SDK. Grava
 * em `profile_photos/{userId}.jpg` (caminho determinístico por usuário —
 * nova foto sobrescreve a antiga em vez de acumular lixo no bucket).
 *
 * Retorna a URL pública de download via `getDownloadUrl`, que já vem com
 * o token de acesso do Storage — pode ser persistida direto no doc do
 * usuário em Firestore (`User.photoUrl`) e lida por qualquer cliente sem
 * necessidade de auth no request HTTP.
 */
class FirebasePhotoStorageRepository : PhotoStorageRepository {
    override suspend fun upload(userId: String, bytes: ByteArray): String {
        val ref = Firebase.storage.reference.child("profile_photos/$userId.jpg")
        ref.putData(Data(bytes))
        return ref.getDownloadUrl()
    }
}
