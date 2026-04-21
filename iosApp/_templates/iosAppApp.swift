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

@main
struct iosAppApp: App {

    init() {
        // `FirebaseApp.configure()` reads `GoogleService-Info.plist` from the
        // main bundle. If the plist is not added to the target membership the
        // call crashes with "Could not locate configuration file".
        FirebaseApp.configure()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
