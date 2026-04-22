# Firestore Security Rules

Este doc descreve as regras de segurança do Firestore do Easy Folgas e como
fazer o deploy.

O arquivo autoritativo fica em [`firestore.rules`](../firestore.rules) na
raiz do repo. `firebase.json` aponta pra ele e pra
[`firestore.indexes.json`](../firestore.indexes.json) (vazio por enquanto —
as queries do app são todas single-field).

## Coleções e regras (resumo)

| Coleção           | Read                             | Create                                                                                         | Update                                                                                                  | Delete |
| ----------------- | -------------------------------- | ---------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- | ------ |
| `users/{uid}`     | autenticado                      | `auth.uid == uid` + email bate com token + role = USER (ou ADMIN se email em `bootstrapAdmins`) | admin OU próprio usuário (id/email/createdAt imutáveis; role muda só via bootstrap repair ou por admin) | ❌     |
| `allowed_emails`  | GET: autenticado / LIST: admin   | admin                                                                                          | admin                                                                                                   | admin  |
| `folgas/{id}`     | autenticado                      | `auth.uid == userId` + status = SCHEDULED                                                      | dono cancela (SCHEDULED→CANCELLED), aceite de troca flipa (SCHEDULED→SWAPPED + userId muda), ou admin   | ❌     |
| `swaps/{id}`      | participante (requester/target) ou admin | `auth.uid == requesterId` + status = PENDING + requester ≠ target                          | target aceita/rejeita, requester cancela (só transições a partir de PENDING), campos-chave imutáveis, ou admin | ❌     |
| qualquer outro    | ❌                               | ❌                                                                                             | ❌                                                                                                      | ❌     |

## Lista de admins bootstrap

A função `bootstrapAdmins()` dentro do `firestore.rules` precisa ficar
**sincronizada** com `AdminBootstrap.ADMIN_EMAILS` em
[`AdminBootstrap.kt`](../composeApp/src/commonMain/kotlin/app/folga/domain/AdminBootstrap.kt).

Se você alterar a lista hardcoded no app, altere também nas regras e faça o
deploy descrito abaixo. Sem isso, um admin bootstrap novo não consegue
autopromover-se pela primeira vez (o caminho `repairBootstrapRole` falha com
permission-denied).

## Limitações conhecidas

- **Flip de folga durante swap accept (`SCHEDULED → SWAPPED`)**: a regra não
  consegue verificar via query que existe de fato uma troca PENDING entre as
  duas folgas e que o chamador é o target dessa troca — Firestore rules não
  suportam queries em rules. Aceitamos o risco residual (um autenticado
  conseguiria forjar um flip trocando o `userId` pra qualquer user existente)
  porque:
  - O guard real de ordem é o transaction de `FirestoreSwapRepository.accept`
    no cliente, que só roda a partir do botão "Aceitar" na UI.
  - Um flip forjado fora do fluxo de troca deixaria o swap orfão no estado
    PENDING, então a inconsistência seria visível.
  - Se virar problema real, a solução é mover o accept pra uma Cloud Function
    (admin SDK, bypassa rules) e recusar o caminho 2 aqui.

- **Status de `SwapRequest`**: campos como `message` e `respondedAt` não são
  validados rigorosamente. A regra só trava os campos-chave
  (`requesterId`/`targetId`/`fromFolgaId`/`toFolgaId`) e a transição de
  status.

## Como fazer deploy

1. Instale o Firebase CLI (uma vez por máquina):
   ```bash
   npm install -g firebase-tools
   ```

2. Faça login:
   ```bash
   firebase login
   ```

3. Vincule o projeto (uma vez por clone):
   ```bash
   firebase use appfolgaandroid
   ```
   `appfolgaandroid` é o project id do Easy Folgas. Alternativamente, rode
   `firebase use --add` e selecione na lista.

4. Deploy das regras (da raiz do repo):
   ```bash
   firebase deploy --only firestore:rules
   ```

5. Conferir no console:
   - Abra https://console.firebase.google.com/project/appfolgaandroid/firestore/rules
   - A aba **Regras** deve mostrar o conteúdo do `firestore.rules` e a
     revisão/histórico deve ter um novo deploy com a data atual.

### Testar com o emulador local (opcional mas recomendado)

Antes de fazer deploy em produção, roda o emulador:

```bash
firebase emulators:start --only firestore
```

Isso sobe o Firestore local em `localhost:8080` com as regras aplicadas.
Pode apontar o app pra esse emulador durante o desenvolvimento (`useEmulator`
na SDK) pra validar que as regras não quebram os fluxos.

## Como rollback

As regras ficam versionadas no console do Firebase. Se o deploy novo
quebrar algum fluxo:

1. Abra https://console.firebase.google.com/project/appfolgaandroid/firestore/rules
2. Aba **Regras** → botão **Histórico** (ou "Ver versões anteriores")
3. Selecione a revisão anterior → **Reverter para esta versão** →
   confirma o deploy.

O rollback é imediato (não precisa redeploy via CLI).
