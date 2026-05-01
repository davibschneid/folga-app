package app.folga.android

import app.folga.domain.NativeMessagingService
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

class AndroidNativeMessagingService : NativeMessagingService {
    override suspend fun getRegistrationToken(): String? {
        return runCatching {
            FirebaseMessaging.getInstance().token.await()
        }.getOrNull()
    }
}
