package app.folga.auth

import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * iOS bridge between the shared Kotlin code and the Swift-side GoogleSignIn
 * SDK. The Kotlin side keeps no CocoaPods / SPM dependency so that the KMP
 * build does not require those toolchains just to compile the shared
 * framework. Instead the iOS host provides an implementation of this
 * interface at startup, and [IosGoogleSignInProvider] delegates to it.
 *
 * See `iosApp/_templates/GoogleSignInBridge.swift` for the Swift reference
 * implementation and `iosApp/README.md` for how to wire it in Xcode.
 */
interface IosGoogleSignInBridge {
    /**
     * Presents the Google Sign-In flow. Implementations must resume the
     * continuation exactly once (via [onSuccess], [onCancelled] or [onFailure]).
     */
    fun presentSignIn(
        onSuccess: (idToken: String, email: String, displayName: String) -> Unit,
        onCancelled: () -> Unit,
        onFailure: (message: String) -> Unit,
    )
}

/**
 * Registry for the Swift-provided [IosGoogleSignInBridge]. `iosAppApp.swift`
 * installs the bridge on launch; until then the provider returns a
 * deterministic failure so the login screen can render a useful message
 * instead of crashing.
 */
object IosGoogleSignInRegistry {
    var bridge: IosGoogleSignInBridge? = null
}

class IosGoogleSignInProvider : GoogleSignInProvider {
    override suspend fun signIn(): GoogleSignInResult {
        val bridge = IosGoogleSignInRegistry.bridge
            ?: return GoogleSignInResult.Failure(
                "GoogleSignIn não inicializado no iOS (falta registrar o bridge)."
            )

        return suspendCancellableCoroutine { cont ->
            bridge.presentSignIn(
                onSuccess = { idToken, email, displayName ->
                    if (cont.isActive) {
                        cont.resumeWith(
                            Result.success(
                                GoogleSignInResult.Success(
                                    idToken = idToken,
                                    email = email,
                                    displayName = displayName,
                                )
                            )
                        )
                    }
                },
                onCancelled = {
                    if (cont.isActive) {
                        cont.resumeWith(Result.success(GoogleSignInResult.Cancelled))
                    }
                },
                onFailure = { message ->
                    if (cont.isActive) {
                        cont.resumeWith(
                            Result.success(GoogleSignInResult.Failure(message))
                        )
                    }
                },
            )
        }
    }
}
