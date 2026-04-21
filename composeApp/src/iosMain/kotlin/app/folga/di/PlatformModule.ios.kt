package app.folga.di

import org.koin.dsl.module

actual val platformModule = module {
    // No platform-specific bindings: Firestore + Firebase Auth are initialized
    // by the iOS host calling `FirebaseApp.configure()` at startup (see
    // iosApp/README.md).
}
