# iosApp — passo a passo para gerar o projeto Xcode

Este diretório é o host **iOS** do app. Ele consome o framework `ComposeApp`
produzido pelo módulo KMP em [`/composeApp`](../composeApp). Como o Xcode
precisa de macOS pra rodar, o projeto `.xcodeproj` não pode ser gerado nas VMs
de CI (Linux) — você só precisa gerar **uma vez** na sua máquina macOS e
commitar `iosApp/iosApp.xcodeproj` + os fontes Swift. Depois disso qualquer
Mac do time abre `iosApp.xcodeproj` e dá Run.

> Pré-req: macOS 14+, Xcode 15+, Command Line Tools instaladas
> (`xcode-select --install`), JDK 17 (`brew install --cask temurin@17`).

Os fontes Swift prontos pra colar estão em [`_templates/`](./_templates).
Mantemos eles aqui no repo fora do `.xcodeproj` pra versionar o que vai
dentro do target independente de gerar o projeto Xcode; depois que o
`.xcodeproj` existir, basta arrastar os arquivos dessa pasta pro target
`iosApp` no Xcode.

---

## 1. Criar o projeto Xcode

1. Abra o Xcode → **File → New → Project…**
2. Escolha **iOS → App** e clique *Next*.
3. Preencha:
   - **Product Name**: `iosApp`
   - **Team**: qualquer (pra rodar no simulador "Personal Team" basta; pra
     device físico é preciso conta de developer)
   - **Organization Identifier**: `app.folga` (fica `app.folga.iosApp`)
   - **Bundle Identifier**: em **Signing & Capabilities** depois, troque pra
     `app.folga.ios` (é o bundle id cadastrado no projeto Firebase
     `appfolgaandroid`; tem que bater com o `GoogleService-Info.plist`).
   - **Interface**: SwiftUI
   - **Language**: Swift
   - **Storage**: None
   - Deixe desmarcados *Include Tests* e *Use Core Data*.
4. No diálogo de local, **escolha a pasta `folga-app/iosApp/`** (o Xcode vai
   criar `iosApp/iosApp.xcodeproj` + a pasta `iosApp/iosApp/` com os fontes
   gerados). Se o Xcode reclamar que a pasta já existe (porque já tem o
   `GoogleService-Info.plist` e `_templates/`), confirme sobrescrever —
   ele só toca nos arquivos do target, não mexe nos outros.

Quando terminar você deve ter a árvore:

```
iosApp/
├── iosApp.xcodeproj/
├── iosApp/
│   ├── iosAppApp.swift           # gerado; vamos substituir
│   ├── ContentView.swift         # gerado; vamos substituir
│   ├── Assets.xcassets/
│   ├── Preview Content/
│   └── GoogleService-Info.plist  # já commitado no repo
├── _templates/                    # arquivos prontos pra copiar pro target
└── README.md                      # este arquivo
```

## 2. Trocar `iosAppApp.swift` e `ContentView.swift` pelos templates

No Xcode, abra `iosAppApp.swift` e `ContentView.swift` e substitua o conteúdo
pelo conteúdo de [`_templates/iosAppApp.swift`](./_templates/iosAppApp.swift)
e [`_templates/ContentView.swift`](./_templates/ContentView.swift).

Resumo do que eles fazem:

- `iosAppApp.swift` chama `FirebaseApp.configure()` **antes** de qualquer
  coisa, pra Firebase Auth e Firestore funcionarem.
- `ContentView.swift` hospeda o `MainViewController` vindo do KMP, o mesmo
  que o Android consome via `MainActivity`.

## 3. Adicionar o `GoogleService-Info.plist` ao target

O arquivo já está em `iosApp/iosApp/GoogleService-Info.plist` (commitado
no repo). No navegador de arquivos do Xcode, arraste-o pra dentro do target
`iosApp` e marque **Copy items if needed = OFF** e **Add to targets =
iosApp**. Sem isso o `FirebaseApp.configure()` crasha com
`Could not locate configuration file`.

## 4. Adicionar Firebase + GoogleSignIn (SPM)

Recomendo SPM (vem com o Xcode, sem CocoaPods). Em **File → Add Package
Dependencies…**, adicione dois pacotes:

1. **Firebase iOS SDK** — `https://github.com/firebase/firebase-ios-sdk`
   - Version: `11.0.0` ou superior
   - Products a marcar no target `iosApp`:
     - `FirebaseAuth`
     - `FirebaseFirestore`
     - `FirebaseMessaging`

2. **GoogleSignIn-iOS** — `https://github.com/google/GoogleSignIn-iOS`
   - Version: `7.1.0` ou superior
   - Products a marcar no target `iosApp`:
     - `GoogleSignIn`
     - `GoogleSignInSwift`

### 4.1 Copiar `GoogleSignInBridge.swift` e `MessagingBridge.swift` pro target

No Xcode: arraste `_templates/GoogleSignInBridge.swift` e `_templates/MessagingBridge.swift` pra dentro do target
iosApp (mesmo jeito que os outros templates). Esses arquivos são o Swift-side
dos bridges que o Kotlin shared usa pra chamar o SDK nativo.
O `iosAppApp.swift` do template já instala os bridges em `init()` via
`GoogleSignInBridgeBootstrap.install()` e `MessagingBridgeBootstrap.install()`.

### 4.2 URL scheme (`REVERSED_CLIENT_ID`) no Info.plist

O GoogleSignIn SDK volta do browser via um custom URL scheme. Abra seu
`GoogleService-Info.plist` e copie o valor de `REVERSED_CLIENT_ID` (algo como
`com.googleusercontent.apps.123456789-abcdef`). Depois em **Signing &
Capabilities → Info → URL Types → +** crie uma entrada com:

- **Identifier**: `google`
- **URL Schemes**: (cole o `REVERSED_CLIENT_ID`)

Sem isso, o Google Sign-In abre o consentimento mas **nunca volta pro app**
— ele fica preso na tela do browser Safari.

### 4.3 Conferir o `iosAppApp.swift`

O template atualizado já tem:

```swift
init() {
    FirebaseApp.configure()
    GoogleSignInBridgeBootstrap.install()
}

var body: some Scene {
    WindowGroup {
        ContentView()
            .onOpenURL { url in
                GIDSignIn.sharedInstance.handle(url)
            }
    }
}
```

O `onOpenURL` é quem recebe o callback do OAuth e passa pro SDK fechar o
fluxo. Sem essa linha o app cai no `RootViewController`, ignora o callback
e o `signIn(withPresenting:)` fica esperando pra sempre.

> Opção CocoaPods (se preferir): veja a seção [CocoaPods alternativo](#cocoapods-alternativo)
> no fim deste arquivo. Os dois caminhos são equivalentes; não misture.

## 5. Embutir o framework `ComposeApp`

O KMP compila o `ComposeApp.framework` e coloca em
`composeApp/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`. Precisamos
que cada build do Xcode dispare esse Gradle task e linke o resultado.

### 5.1 Run Script Phase

No target `iosApp` → **Build Phases** → **+** → **New Run Script Phase** e
posicione o novo phase **antes** de *Compile Sources* (arraste pra cima).
Nome: `Build & Embed ComposeApp framework`. Shell:

```bash
cd "$SRCROOT/.."
./gradlew :composeApp:embedAndSignAppleFrameworkForXcode
```

Desmarque *Based on dependency analysis* pra rodar sempre.

### 5.2 Framework Search Paths

No mesmo target → **Build Settings** → busque por *Framework Search Paths*
e adicione (para Debug e Release):

```
$(SRCROOT)/../composeApp/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)
```

### 5.3 Link do framework

Target `iosApp` → **General** → **Frameworks, Libraries, and Embedded
Content** → **+** → *Add Other…* → *Add Files…* → selecione
`../composeApp/build/xcode-frameworks/Debug/iphoneos/ComposeApp.framework`
(o path pode ainda não existir; nesse caso rode
`./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` uma vez e volte).
Marque **Embed & Sign**.

## 6. Build & Run

1. Escolha um simulator (`iPhone 15 Pro`) no Xcode.
2. ⌘R.
3. Primeira build baixa as dependências SPM e roda o Gradle — leva uns 3–5
   min. Builds subsequentes são incrementais (<30s pro Gradle task).

Quando subir, você deve ver a tela de Login idêntica à do Android. Crie
uma conta, reserve folga, confira no Firebase Console:

- Authentication → Users: o usuário deve aparecer.
- Firestore → `users/{uid}`, `folgas/{id}`, `swaps/{id}`: os docs devem
  aparecer conforme você usa o app. Funciona instantaneamente se você
  manter a tela aberta e alterar o Firestore pelo console.

## 7. O que commitar / ignorar

Commite:

- `iosApp/iosApp.xcodeproj/` (o Xcode tem seu próprio merge driver pra
  `project.pbxproj`; conflitos são chatos mas resolvíveis)
- `iosApp/iosApp/*.swift`
- `iosApp/iosApp/Info.plist`
- `iosApp/iosApp/Assets.xcassets/`

Não commite (o `.gitignore` já cobre):

- `iosApp/build/`
- `iosApp/Pods/`
- `iosApp/*.xcworkspace` (gerado pelo CocoaPods)
- `*.xcuserstate`, `xcuserdata/`, `DerivedData/`

## 8. CI para iOS (opcional, futuro)

A CI atual (`.github/workflows/android.yml`) só roda no Ubuntu e só builda
Android. Pra rodar build de iOS no CI:

- GitHub Actions runner `macos-14`
- Comando: `xcodebuild -scheme iosApp -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 15' build`

Isso custa minutos Mac mais caros — deixei fora do MVP, mas o passo acima é
literalmente o que precisa entrar num novo workflow quando fizer sentido.

---

## CocoaPods alternativo

Caso prefira Pods:

1. `sudo gem install cocoapods` (ou `brew install cocoapods`).
2. Crie `iosApp/Podfile` com:
   ```ruby
   platform :ios, '15.0'
   use_frameworks!

   target 'iosApp' do
     pod 'FirebaseAuth', '~> 11.0'
     pod 'FirebaseFirestore', '~> 11.0'
     pod 'GoogleSignIn', '~> 7.1'
     pod 'GoogleSignInSwiftSupport', '~> 7.1'
   end
   ```
3. `cd iosApp && pod install` — a partir daí abra o `.xcworkspace`, não o
   `.xcodeproj`.
4. Pule a etapa **4 (SPM)** acima; o resto é igual.

---

## Troubleshooting

- **`Could not locate configuration file GoogleService-Info.plist`** →
  arraste o plist pro target como na etapa 3 (`Target Membership = iosApp`).
- **`Undefined symbol: _kfun:app.folga...`** → a Run Script Phase da etapa 5.1
  não rodou antes de *Compile Sources*. Arraste ela pra cima na lista de
  Build Phases.
- **`No such module 'ComposeApp'`** → `Framework Search Paths` não aponta pra
  pasta do framework buildado. Confira a etapa 5.2.
- **Simulator Arm (Apple Silicon) não linka `ComposeApp`** → garanta que o
  target Kotlin `iosSimulatorArm64` está em `composeApp/build.gradle.kts`
  (já está) e que você rodou a primeira build pelo menos uma vez pra criar a
  pasta `xcode-frameworks`.
- **`FirebaseApp.configure()` crasha com "default app has not been
  configured"** → a chamada precisa estar dentro do `init()` do `App`, antes
  do primeiro acesso a `Auth.auth()` / `Firestore.firestore()`. O template já
  faz isso.
- **Tocar em "Entrar com Google" mostra "GoogleSignIn não inicializado no
  iOS..."** → você esqueceu de copiar o `GoogleSignInBridge.swift` pro
  target, ou o `GoogleSignInBridgeBootstrap.install()` não roda no `init()`.
  Confira §4.1 / §4.3.
- **O browser abre mas nunca volta pro app** → faltou o URL Type com o
  `REVERSED_CLIENT_ID`, ou faltou o `.onOpenURL { GIDSignIn.sharedInstance.handle(url) }`
  no `iosAppApp.swift`. Confira §4.2 e §4.3.
- **Gradle task falha com `Could not connect to daemon`** → desabilite o
  Gradle daemon no Xcode: em *Preferences → Locations → Command Line Tools*
  garanta que está apontando pra Xcode 15+, e adicione
  `org.gradle.daemon=false` em `gradle.properties` se precisar.
