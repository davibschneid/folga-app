# Deploy — Play Console (Android)

Passo a passo pra publicar o `folga-app` na **Google Play Console** a partir
desta branch / repositório. Este guia cobre o ciclo completo: gerar a
*upload key*, configurar os *GitHub Secrets*, gerar o `.aab` assinado via
CI e subir na track **Internal testing**.

> TL;DR: gere a keystore 1 vez → cadastre 4 secrets no GitHub → faça um
> push de tag `v0.1.1` → baixe o `.aab` do Actions → suba no Play Console.

---

## 1. Pré-requisitos

- Conta Google com acesso à **Google Play Console** — se ainda não tem,
  registro único de **US$ 25** em https://play.google.com/console/signup.
- Permissão de push de tags neste repo.
- `keytool` local (vem com o JDK 17 instalado — `which keytool`).

## 2. Gerar a upload keystore (uma vez por app, pra sempre)

> ⚠️ **Guarde o arquivo `.jks` E as senhas em um gerenciador de senhas
> antes de qualquer coisa.** Se você perder a upload key, só consegue
> recuperar via suporte do Google Play (e pode bloquear publicações por
> dias). **Nunca** commite esse arquivo no repo.

No terminal, em qualquer pasta:

```bash
keytool -genkeypair -v \
  -keystore upload-keystore.jks \
  -alias upload \
  -keyalg RSA -keysize 2048 \
  -validity 10000 \
  -dname "CN=Davi Schneid, OU=folga-app, O=folga-app, L=Porto Alegre, S=RS, C=BR"
```

O `keytool` pergunta duas senhas (store password e key password) — use a
mesma pra simplificar. Anote ambas no gerenciador de senhas.

Output: `upload-keystore.jks`. Este é o arquivo que **você** guarda; **não
entra no Git** (o `.gitignore` já bloqueia `*.jks`).

### Opcional: build local antes de publicar

Se quiser gerar o `.aab` na sua máquina antes de configurar o CI:

```bash
# Copie upload-keystore.jks pra raiz do repo (ou deixe em outro lugar e
# referencie por path absoluto na linha abaixo).
cat > keystore.properties <<EOF
KEYSTORE_FILE=upload-keystore.jks
KEYSTORE_PASSWORD=<sua senha>
KEY_ALIAS=upload
KEY_PASSWORD=<sua senha>
EOF

./gradlew :composeApp:bundleRelease
# saída: composeApp/build/outputs/bundle/release/composeApp-release.aab
```

`keystore.properties` também está no `.gitignore`; nunca commite.

## 3. Configurar os GitHub Secrets

Em **GitHub → repositório `davibschneid/folga-app` → Settings → Secrets and
variables → Actions → New repository secret**, crie os 4 secrets abaixo:

| Secret              | Valor                                                              |
|---------------------|--------------------------------------------------------------------|
| `KEYSTORE_BASE64`   | `base64 -w0 upload-keystore.jks` (cole a string gigante de uma linha) |
| `KEYSTORE_PASSWORD` | store password da keystore                                         |
| `KEY_ALIAS`         | `upload` (ou o alias que você escolheu no `keytool -alias`)        |
| `KEY_PASSWORD`      | key password da keystore                                           |

Comando pra gerar o base64:

```bash
# Linux
base64 -w0 upload-keystore.jks | pbcopy        # com pbcopy instalado
base64 -w0 upload-keystore.jks > keystore.b64  # ou manda pra arquivo

# macOS
base64 -i upload-keystore.jks | pbcopy
```

## 4. Gerar o `.aab` via CI

O workflow [`.github/workflows/android-release.yml`](.github/workflows/android-release.yml)
roda em dois gatilhos:

- **Push de tag** `v*` — típico fluxo de release.
- **"Run workflow"** manual na aba Actions (útil pra QA em cima de uma
  branch específica, sem criar tag).

Fluxo normal:

```bash
# bump versionCode/versionName em composeApp/build.gradle.kts antes de taggear
git tag v0.1.1
git push origin v0.1.1
```

Acompanhe em **Actions → Android Release (.aab)**. Ao terminar, baixe os
artefatos:

- `folga-app-release-aab` → `composeApp-release.aab` (é este que vai pra Play)
- `folga-app-release-apk` → `composeApp-release.apk` (pra sideload / QA)

## 5. Configurar o app na Play Console (primeira vez)

1. https://play.google.com/console → **Create app**.
2. Preencha:
   - **App name**: `folga-app` (pode mudar depois)
   - **Default language**: Português (Brasil)
   - **App or game**: App
   - **Free or paid**: Free
   - Aceite os termos de declaração.
3. No menu lateral do app, complete pelo menos:
   - **Store presence → Main store listing**: ícone 512×512, feature graphic
     1024×500, ≥2 screenshots do celular, descrição curta + longa, categoria,
     email de contato, **link pra política de privacidade** (obrigatório, pode
     ser uma página simples publicada no GitHub Pages).
   - **Policy → App content**:
     - Privacy Policy URL
     - Ads: No
     - App access: "All functionality is available without special access" —
       se houver login obrigatório pra testar, marque a outra opção e
       forneça email/senha de teste.
     - Content rating: responda o questionário.
     - Target audience: escolha faixa etária.
     - Data safety: declare que coleta email + auth (Firebase Auth).
     - Government app: No.
     - Financial features: No.
4. **Release → Testing → Internal testing → Create new release**:
   - *App integrity*: escolha **Use Play App Signing** (padrão).
     Na primeira release, a Play gera a *app signing key* e a sua upload key
     (do passo 2) vai ser a chave que você usa pra assinar todo upload
     futuro. Isso é o que o Google recomenda hoje.
   - Upload o `composeApp-release.aab` baixado do Actions.
   - *Release name*: `0.1.1` (ou o que vier do `versionName`).
   - *Release notes*: "Primeira release pra teste interno." em cada idioma.
   - **Save → Review release → Start rollout to Internal testing**.
5. **Testers**: em *Internal testing → Testers*, crie uma *Email list* com
   os endereços (até 100) e publique. Cada tester recebe um **opt-in URL**:
   abre no celular → toca *Become a tester* → instala o app direto da Play
   Store.

## 6. Releases subsequentes

Pra publicar uma nova versão:

1. Bump `versionCode` (sempre +1, sem pular) e `versionName` em
   <ref_snippet file="/home/ubuntu/repos/folga-app/composeApp/build.gradle.kts" lines="96-102" />.
2. Commit + push.
3. `git tag v0.1.2 && git push origin v0.1.2`.
4. Baixe o `.aab` do Actions, suba em **Internal testing → Create new
   release**. O Play detecta que o `versionCode` subiu e libera o upload.

## 7. Promover entre tracks

Quando o teste interno estiver ok, **Play Console → Internal testing →
Promote release → Closed testing / Open testing / Production**.

- **Closed testing**: grupos maiores (amigos, colegas), ainda sem review
  completo (mais rápido).
- **Open testing**: qualquer um com o link opta por baixar — aparece como
  "Early access" na Play Store.
- **Production**: requer review do Google (costuma levar alguns dias na
  primeira submissão).

## 8. Troubleshooting

- **"Upload failed, the signature scheme is invalid"** → o `.aab` não foi
  assinado. Confira se os 4 secrets estão setados no GitHub e se o job
  `Build Android App Bundle (release)` no Actions rodou até o fim.
- **"You uploaded an APK or Android App Bundle with an invalid signature"**
  → a Play Console já viu um upload com uma *upload key* diferente pra
  esse `applicationId`. Você precisa usar a mesma keystore do primeiro
  upload — se perdeu, abra ticket em *Play Console → Help → Contact
  support → Request upload key reset*.
- **"versionCode X has already been used"** → bump o `versionCode` em
  `composeApp/build.gradle.kts` e refaça a tag.
- **App crasha só em release** com `NoClassDefFoundError` ou
  `SerializationException` → R8 removeu alguma classe. Adicione um
  `-keep` em <ref_file file="/home/ubuntu/repos/folga-app/composeApp/proguard-rules.pro" />
  apontando pro pacote que falhou, e rebuilde.
