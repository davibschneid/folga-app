# folga-app

App multiplataforma (Android + iOS) para **reserva de folga de colaboradores** e **troca de folga entre colegas**.

Construído com **Kotlin Multiplatform (KMP) + Compose Multiplatform** — um único código de UI em Kotlin rodando nativamente em Android e iOS.

## Stack

- **Kotlin Multiplatform** 2.0.x
- **Compose Multiplatform** 1.7.x (UI compartilhada para Android e iOS)
- **Firebase** (via [GitLive SDK](https://github.com/GitLiveApp/firebase-kotlin-sdk))
  - Auth: Google Sign-In + email/senha
  - Firestore: dados compartilhados entre usuários (reservas e trocas)
- **SQLDelight** 2.x: cache local tipado
- **Coroutines + Flow** para async
- **Koin** para injeção de dependência
- **kotlinx.datetime** para datas
- **kotlinx.serialization** para DTOs

## Estrutura

```
folga-app/
├── composeApp/          # Módulo principal (targets Android + iOS)
│   └── src/
│       ├── commonMain/  # UI Compose + lógica compartilhada
│       ├── androidMain/ # Entrypoint Android (MainActivity, Application)
│       └── iosMain/     # Entrypoint iOS (MainViewController)
├── iosApp/              # Projeto Xcode que consome o framework KMP
└── gradle/
    └── libs.versions.toml
```

## Funcionalidades (MVP)

- [x] Estrutura base KMP + Compose Multiplatform
- [ ] Login com Google
- [x] Login/cadastro com email e senha (via Firebase Auth)
- [x] Cadastro de colaborador (nome, email, matrícula, equipe)
- [ ] Reservar folga (escolher data)
- [ ] Listar minhas folgas
- [ ] Solicitar troca de folga com colega
- [ ] Aceitar/recusar troca recebida
- [ ] Histórico de trocas

## Como rodar

### Pré-requisitos
- JDK 17
- Android Studio Ladybug+ (com plugin Kotlin Multiplatform)
- Xcode 15+ (para iOS, precisa de macOS)
- Conta Firebase com um projeto criado

### Configuração Firebase
Projeto Firebase **appfolgaandroid** já provisionado. Os arquivos de configuração
estão versionados no repo:
- `composeApp/google-services.json` (app Android `app.folga.android`)
- `iosApp/iosApp/GoogleService-Info.plist` (app iOS `app.folga.ios`)

Se precisar regerar: https://console.firebase.google.com → projeto `appfolgaandroid`
→ Project Settings → baixar os arquivos e substituir nos caminhos acima.

Lembrete: para rodar o Google Sign-In em Android é preciso cadastrar o
SHA-1 da sua chave de debug no Firebase Console (Project Settings → SHA
certificate fingerprints).

### Android
```bash
./gradlew :composeApp:assembleDebug
# ou abra em Android Studio e rode o composeApp
```

### iOS
```bash
open iosApp/iosApp.xcodeproj
# Build & Run no Xcode (precisa de macOS)
```

## Licença

MIT
