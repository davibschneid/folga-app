# iosApp/_templates

Swift source templates that go into the `iosApp` target once the Xcode
project exists. These files live at the repo root (outside
`iosApp.xcodeproj`) so they can be versioned independently of whether the
project has been generated yet. See [`../README.md`](../README.md) for the
full walkthrough.

Files:

- `iosAppApp.swift` — entry point. Calls `FirebaseApp.configure()` before
  SwiftUI's scene mounts. Copy into the target replacing the Xcode-generated
  stub.
- `ContentView.swift` — `UIViewControllerRepresentable` that hosts the KMP
  `MainViewController`. The actual UI comes from Compose Multiplatform in
  `composeApp`; iOS is just a launcher.

After generating the project in step 1 of the parent README, copy-paste
these files over the Xcode-generated stubs (or drag them into the target
and remove the originals).
