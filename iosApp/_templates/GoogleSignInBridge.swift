// GoogleSignInBridge.swift
// --------------------------------------------------------------------------
// Swift-side implementation of the `IosGoogleSignInBridge` interface declared
// in the shared KMP code (see composeApp/src/iosMain/kotlin/app/folga/auth/
// IosGoogleSignInProvider.kt).
//
// Why this file exists:
// The shared Kotlin framework must NOT depend on the GoogleSignIn iOS SDK —
// otherwise every `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`
// would need CocoaPods/SPM for a transitive ObjC dep, and the Linux CI would
// not be able to build the shared framework anymore. Instead the SDK lives
// only on the iOS host target (added via SPM, see iosApp/README.md §4) and
// the Kotlin side talks to it through the `IosGoogleSignInBridge` contract.
//
// How to wire it:
// 1) Copy this file into the `iosApp` Xcode target (same way you copied
//    `iosAppApp.swift` / `ContentView.swift`).
// 2) Install the GoogleSignIn SDK via SPM (iosApp/README.md §4).
// 3) In `iosAppApp.swift`, call `GoogleSignInBridgeBootstrap.install()` once
//    after `FirebaseApp.configure()` (see iosApp/README.md §3 for the exact
//    snippet).
// 4) Add the URL scheme from `GoogleService-Info.plist` (REVERSED_CLIENT_ID)
//    to Info.plist under `CFBundleURLTypes` (iosApp/README.md §4.2).
// 5) Handle the OAuth callback URL in `iosAppApp.swift` via
//    `GIDSignIn.sharedInstance.handle(url)` (snippet in iosApp/README.md).
// --------------------------------------------------------------------------

import Foundation
import UIKit
import GoogleSignIn
import ComposeApp

/// Bridges the shared KMP `IosGoogleSignInBridge` to the GoogleSignIn iOS SDK.
///
/// Exposes the `GIDSignIn` flow to Kotlin without leaking any Google types
/// across the bridge — everything flows through plain `String` closures that
/// Kotlin can safely consume.
final class GoogleSignInBridgeImpl: NSObject, IosGoogleSignInBridge {

    /// Must be called from the main thread — `GIDSignIn.signIn(...)` presents
    /// a `SFSafariViewController`/`ASWebAuthenticationSession` and requires a
    /// live `UIViewController` root.
    func presentSignIn(
        onSuccess: @escaping (String, String, String) -> Void,
        onCancelled: @escaping () -> Void,
        onFailure: @escaping (String) -> Void
    ) {
        DispatchQueue.main.async {
            guard let rootVC = Self.topMostViewController() else {
                onFailure("Não foi possível localizar a view controller raiz para apresentar o Google Sign-In.")
                return
            }

            GIDSignIn.sharedInstance.signIn(withPresenting: rootVC) { result, error in
                if let error = error as NSError? {
                    // GIDSignInErrorCode.canceled == -5 — the user backed out
                    // of the sheet; don't treat it as a real failure.
                    if error.domain == kGIDSignInErrorDomain && error.code == GIDSignInError.canceled.rawValue {
                        onCancelled()
                    } else {
                        onFailure(error.localizedDescription)
                    }
                    return
                }

                guard
                    let user = result?.user,
                    let idToken = user.idToken?.tokenString
                else {
                    onFailure("Google Sign-In retornou sem ID token.")
                    return
                }

                let email = user.profile?.email ?? ""
                let displayName = user.profile?.name ?? email
                onSuccess(idToken, email, displayName)
            }
        }
    }

    /// Walks the key window's VC chain so we always present on top of any
    /// sheets / navigation controllers the Compose host may have pushed.
    private static func topMostViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
            ?? UIApplication.shared.connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .first

        let keyWindow = scene?.windows.first(where: { $0.isKeyWindow }) ?? scene?.windows.first
        var top = keyWindow?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}

/// One-shot installer — call `GoogleSignInBridgeBootstrap.install()` once
/// from `iosAppApp.init()` after `FirebaseApp.configure()`. Idempotent.
enum GoogleSignInBridgeBootstrap {
    static func install() {
        IosGoogleSignInRegistry.shared.bridge = GoogleSignInBridgeImpl()
    }
}
