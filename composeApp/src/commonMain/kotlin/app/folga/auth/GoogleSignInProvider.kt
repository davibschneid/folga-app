package app.folga.auth

/**
 * Platform-specific gateway that launches the native Google Sign-In flow and
 * returns the ID token + basic profile for the chosen account.
 *
 * * Android: [GoogleSignInProvider] is backed by Jetpack Credential Manager
 *   (`androidx.credentials` + Sign in with Google / `googleid`). It needs an
 *   `Activity`-scoped `Context` and the OAuth 2.0 Web Client ID from Firebase
 *   (`client_type: 3` inside `google-services.json`).
 * * iOS: the actual implementation is a thin Kotlin facade that delegates to a
 *   Swift helper implementing [IosGoogleSignInBridge] — the Swift side owns
 *   the GoogleSignIn SDK (added via SPM in Xcode). See
 *   `iosApp/_templates/GoogleSignInBridge.swift` for the reference
 *   implementation and `iosApp/README.md` for wiring steps.
 *
 * The returned [GoogleSignInResult] is then handed to [AuthRepository.signInWithGoogleIdToken]
 * which exchanges the token for a Firebase credential.
 */
interface GoogleSignInProvider {
    suspend fun signIn(): GoogleSignInResult
}

/** Outcome of the native Google Sign-In flow. */
sealed interface GoogleSignInResult {
    data class Success(
        val idToken: String,
        val email: String,
        val displayName: String,
    ) : GoogleSignInResult

    /** User dismissed the bottom sheet / account picker. Not an error. */
    data object Cancelled : GoogleSignInResult

    /** Unexpected failure — surfaced to the UI as a snackbar/error text. */
    data class Failure(val message: String) : GoogleSignInResult
}
