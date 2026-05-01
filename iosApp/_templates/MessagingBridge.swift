import Foundation
import FirebaseMessaging
import ComposeApp

/// Bridges the shared KMP `IosMessagingBridge` to the Firebase Messaging iOS SDK.
///
/// This implementation uses the native Firebase Messaging SDK to retrieve the
/// FCM registration token.
final class MessagingBridgeImpl: NSObject, IosMessagingBridge {

    /// Fetches the FCM token asynchronously and returns it via the provided closure.
    func getToken(onResult: @escaping (String?) -> Void) {
        Messaging.messaging().token { token, error in
            if let error = error {
                // Log error but don't crash; the repository handles null as failure.
                print("Error fetching FCM registration token: \(error.localizedDescription)")
                onResult(nil)
            } else {
                onResult(token)
            }
        }
    }
}

/// One-shot installer — call `MessagingBridgeBootstrap.install()` once
/// from `iosAppApp.init()` after `FirebaseApp.configure()`. Idempotent.
enum MessagingBridgeBootstrap {
    static func install() {
        IosMessagingRegistry.shared.bridge = MessagingBridgeImpl()
    }
}
