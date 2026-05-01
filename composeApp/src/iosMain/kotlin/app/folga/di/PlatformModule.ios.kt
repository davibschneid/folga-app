package app.folga.di

import app.folga.IosNativeMessagingService
import app.folga.auth.GoogleSignInProvider
import app.folga.auth.IosGoogleSignInProvider
import app.folga.domain.NativeMessagingService
import org.koin.dsl.module

actual val platformModule = module {
    // Firestore + Firebase Auth are initialized by the iOS host calling
    // `FirebaseApp.configure()` at startup (see iosApp/README.md).

    // The iOS Google Sign-In provider delegates to a Swift-side bridge that
    // the iOS host installs into `IosGoogleSignInRegistry.bridge` on launch.
    // Until the bridge is installed the provider returns a user-visible
    // "not initialised" failure — it never crashes.
    single<GoogleSignInProvider> { IosGoogleSignInProvider() }
    single<NativeMessagingService> { IosNativeMessagingService() }
}
