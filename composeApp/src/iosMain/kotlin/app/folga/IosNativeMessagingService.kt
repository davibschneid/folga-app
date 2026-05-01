package app.folga

import app.folga.domain.NativeMessagingService
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Interface que o lado Swift implementa para fornecer o token FCM.
 */
interface IosMessagingBridge {
    fun getToken(onResult: (String?) -> Unit)
}

/**
 * Registro para o bridge de mensagens no iOS.
 */
object IosMessagingRegistry {
    var bridge: IosMessagingBridge? = null
}

class IosNativeMessagingService : NativeMessagingService {
    override suspend fun getRegistrationToken(): String? {
        val bridge = IosMessagingRegistry.bridge ?: return null
        
        return suspendCancellableCoroutine { cont ->
            bridge.getToken { token ->
                if (cont.isActive) {
                    cont.resumeWith(Result.success(token))
                }
            }
        }
    }
}
