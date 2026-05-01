//
//  iosAppApp.swift
//  iosApp
//
//  Entry point for the iOS host app. Starts Firebase before SwiftUI kicks in
//  so that the Kotlin/Native shared code (ComposeApp framework) can resolve
//  `Firebase.auth` and `Firebase.firestore` as soon as `MainViewController`
//  spins up Koin.
//

import SwiftUI
import FirebaseCore
import GoogleSignIn

@main
struct iosAppApp: App {

    init() {
        // `FirebaseApp.configure()` reads `GoogleService-Info.plist` from the
        // main bundle. If the plist is not added to the target membership the
        // call crashes with "Could not locate configuration file".
        FirebaseApp.configure()

        // Installs the Swift implementation of `IosGoogleSignInBridge` and
        // `IosMessagingBridge` so that the shared Kotlin code can trigger
        // native SDK flows.
        GoogleSignInBridgeBootstrap.install()
        MessagingBridgeBootstrap.install()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                // Hand OAuth callback URLs (google-signin redirect) back to
                // the GoogleSignIn SDK so it can finish the flow.
                .onOpenURL { url in
                    GIDSignIn.sharedInstance.handle(url)
                }
        }
    }
}
