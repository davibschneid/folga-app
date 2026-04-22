package app.folga.di

import app.folga.auth.ActivityContextHolder
import app.folga.auth.CredentialManagerGoogleSignInProvider
import app.folga.auth.GoogleSignInProvider
import org.koin.dsl.module

actual val platformModule = module {
    // Firestore + Firebase Auth are configured by the google-services
    // plugin's ContentProvider on Android; no extra bindings needed for them.

    // Google Sign-In: singleton holder for the current Activity so Credential
    // Manager has somewhere to render its bottom sheet.
    single { ActivityContextHolder() }
    single<GoogleSignInProvider> { CredentialManagerGoogleSignInProvider(get()) }
}
