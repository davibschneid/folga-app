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
- [ ] Login/cadastro com email e senha
- [ ] Cadastro de colaborador (nome, email, matrícula, equipe)
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
1. Crie um projeto em https://console.firebase.google.com
2. Adicione um app Android (package: `app.folga.android`) e baixe `google-services.json` → coloque em `composeApp/`
3. Adicione um app iOS (bundle: `app.folga.ios`) e baixe `GoogleService-Info.plist` → coloque em `iosApp/iosApp/`
4. Habilite Authentication → Google e Email/Password
5. Crie Firestore Database (modo produção)

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
