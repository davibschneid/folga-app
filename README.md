# folga-app

App multiplataforma (Android + iOS) para **reserva de folga de colaboradores** e **troca de folga entre colegas**.

Construído com **Kotlin Multiplatform (KMP) + Compose Multiplatform** — um único código de UI em Kotlin rodando nativamente em Android e iOS.

## Stack

- **Kotlin Multiplatform** 2.0.x
- **Compose Multiplatform** 1.7.x (UI compartilhada para Android e iOS)
- **Firebase** (via [GitLive SDK](https://github.com/GitLiveApp/firebase-kotlin-sdk))
  - Auth: Google Sign-In + email/senha
  - Firestore: dados compartilhados entre dispositivos (perfis, folgas, trocas).
    A persistência offline do próprio SDK do Firestore cobre o cache local, então
    não há SQLite / banco local separado.
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
- [x] Cadastro de colaborador (nome, email, matrícula, equipe) persistido no Firestore
- [x] Reservar folga (escolher data) — Firestore
- [x] Listar minhas folgas — Firestore (tempo real via snapshots)
- [x] Solicitar troca de folga com colega — Firestore
- [x] Aceitar/recusar troca recebida — escrita atômica (batch) no Firestore
- [x] Histórico de trocas (status PENDING / ACCEPTED / REJECTED / CANCELLED)

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

### Coleções Firestore

O app grava em 3 coleções no nível raiz do Firestore:

| Coleção  | Document id                 | Campos                                                                 |
|----------|-----------------------------|------------------------------------------------------------------------|
| `users`  | Firebase Auth `uid`         | `email`, `name`, `registrationNumber`, `team`, `createdAt` (epoch ms)  |
| `folgas` | id gerado pelo Firestore    | `userId`, `date` ("YYYY-MM-DD"), `status`, `note?`, `createdAt`        |
| `swaps`  | id gerado pelo Firestore    | `fromFolgaId`, `toFolgaId`, `requesterId`, `targetId`, `status`, `message?`, `createdAt`, `respondedAt?` |

`status` em `folgas` é um de `SCHEDULED / COMPLETED / SWAPPED / CANCELLED` e
em `swaps` é um de `PENDING / ACCEPTED / REJECTED / CANCELLED` (serializados
como string). Aceitar uma troca flipa os `userId` das duas folgas e marca o
swap como `ACCEPTED` em uma única `WriteBatch` — atomicidade garantida pelo
Firestore.

#### Security rules sugeridas (MVP)

O MVP grava direto do cliente, então as regras abaixo exigem apenas que o
usuário esteja autenticado para ler/escrever. Para produção é recomendável
endurecer para limitar ownership e campos editáveis.

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{uid} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && request.auth.uid == uid;
    }
    match /folgas/{folgaId} {
      allow read: if request.auth != null;
      allow create: if request.auth != null
        && request.resource.data.userId == request.auth.uid;
      // Troca: qualquer usuário autenticado pode escrever porque a
      // aceitação faz batch dos dois lados. Para produção, mover o
      // accept para uma Cloud Function com custom claims.
      allow update, delete: if request.auth != null;
    }
    match /swaps/{swapId} {
      allow read: if request.auth != null;
      allow create: if request.auth != null
        && request.resource.data.requesterId == request.auth.uid;
      allow update: if request.auth != null;
    }
  }
}
```

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
