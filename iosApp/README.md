# iosApp

Projeto Xcode que consome o framework `ComposeApp` gerado pelo módulo KMP em `/composeApp`.

## Estrutura mínima esperada

A estrutura do Xcode precisa ser gerada uma vez em uma máquina com macOS (o repositório roda CI só para Android no momento). Passos:

1. Em macOS com Xcode 15+ instalado, rode:
   ```bash
   cd iosApp
   # Crie um app iOS SwiftUI chamado "iosApp", bundle id app.folga.ios, Team = Personal
   # Depois adicione o framework ComposeApp via "Embed Frameworks" → "Embed & Sign"
   ```
2. Em `Build Phases → New Run Script Phase` (antes de Compile Sources):
   ```bash
   cd "$SRCROOT/.."
   ./gradlew :composeApp:embedAndSignAppleFrameworkForXcode
   ```
3. Em `Framework Search Paths` adicione `$(SRCROOT)/../composeApp/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`.
4. No `ContentView.swift`:
   ```swift
   import SwiftUI
   import ComposeApp

   struct ContentView: View {
       var body: some View {
           ComposeView().ignoresSafeArea(.keyboard)
       }
   }

   struct ComposeView: UIViewControllerRepresentable {
       func makeUIViewController(context: Context) -> UIViewController {
           MainViewControllerKt.MainViewController()
       }
       func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
   }
   ```

Depois de gerado, commite `iosApp/iosApp.xcodeproj` e `iosApp/iosApp/` no repositório.

## Firebase no iOS

Para o Firebase (Auth + Firestore) funcionar no iOS:

1. Adicione os SDKs `Firebase/Auth` **e** `Firebase/Firestore` via Swift Package Manager ou CocoaPods no `iosApp.xcodeproj`.
2. Arraste `iosApp/iosApp/GoogleService-Info.plist` (já commitado no repo) para o target `iosApp` no Xcode.
3. Em `iosAppApp.swift` (ou `AppDelegate.swift`), chame `FirebaseApp.configure()` **antes** de qualquer chamada para Firebase:
   ```swift
   import SwiftUI
   import FirebaseCore

   @main
   struct iosAppApp: App {
       init() {
           FirebaseApp.configure()
       }
       var body: some Scene {
           WindowGroup { ContentView() }
       }
   }
   ```
4. No Android o `google-services` plugin faz essa inicialização automaticamente — nada a fazer do lado Kotlin.
