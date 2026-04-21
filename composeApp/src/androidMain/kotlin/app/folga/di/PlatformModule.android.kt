package app.folga.di

import org.koin.dsl.module

actual val platformModule = module {
    // No platform-specific bindings: Firestore + Firebase Auth are configured
    // by the google-services plugin's ContentProvider on Android.
}
