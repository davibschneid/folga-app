//
//  ContentView.swift
//  iosApp
//
//  Thin SwiftUI wrapper that hosts the KMP `MainViewController` returned by
//  the shared Kotlin module (`composeApp/src/iosMain/.../MainViewController.kt`).
//  Everything inside the view comes from the shared Compose Multiplatform UI
//  — the iOS target is just a launcher.
//

import SwiftUI
import ComposeApp

struct ContentView: View {
    var body: some View {
        ComposeView()
            // Avoid the keyboard pushing the whole Compose tree up. Compose
            // handles its own insets internally.
            .ignoresSafeArea(.keyboard)
    }
}

private struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // No-op: the Compose view controller manages its own lifecycle.
    }
}
