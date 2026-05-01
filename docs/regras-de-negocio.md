# Regras de negócio — Easy Folgas

Documento vivo com as regras de negócio do app, pensado como referência para
suporte ao usuário e para o time de desenvolvimento. Cada seção indica o
status da regra (✅ implementada, 🚧 pendente, 💡 planejada) e o PR onde ela
entrou (quando aplicável).

## 1. Cadastro e perfil de usuário

### 1.1 Login ✅ (PR #2, PR #8, PR #47, PR #49, PR #50)
- Permitido apenas via **e-mail/senha** (Firebase Auth) ou **Google Sign-In**.
- Após o sign-up com Google, o usuário é direcionado à tela **Completar
  cadastro** para preencher dados que o Google não fornece (matrícula,
  equipe, turno).
- Autenticação fica em `Authentication → Users` no Firebase Console.
- **Layout (PR #47):** tela com gradient de fundo, formas decorativas,
  campos arredondados e botões de 56dp. Mantém o branding do app
  ("EasyShift") e separa "Entrar com Google" do botão principal.

### 1.1.1 Esqueci minha senha ✅ (PR #49, PR #50)

> **Regra:** o usuário pode redefinir a senha pelo fluxo padrão do
> Firebase (e-mail com link de redefinição). Para evitar enumeração de
> e-mails cadastrados, o app só dispara o envio se o e-mail estiver na
> whitelist (`allowed_emails`).

- Link **"Esqueci minha senha"** abaixo do botão de login.
- Ao clicar, o app pede o e-mail e chama
  `FirebaseAuthRepository.sendPasswordResetEmail(email)`.
- **Gate:** antes de chamar o Firebase, valida que o e-mail está em
  `allowed_emails` (não em `users` — o doc do `users` exige
  `request.auth != null` nas Firestore rules; `allowed_emails` tem
  `get` público pra esse cenário).
- Mensagens:
  - Sucesso: _"Se este e-mail estiver cadastrado, você receberá um
    link de redefinição."_ (mesma resposta pra qualquer e-mail —
    evita confirmar se a conta existe).
  - Erro de rede ou Firebase: _"Erro ao enviar e-mail de
    redefinição."_
- O envio efetivo do e-mail (template, idioma, sender) é configurado
  em **Firebase Console → Authentication → Templates**.

### 1.2 Campos do perfil ✅ (PR #3, PR #8)
Armazenados em `users/{uid}` no Firestore:

| Campo                | Tipo      | Origem                                      |
| -------------------- | --------- | ------------------------------------------- |
| `id`                 | String    | UID do Firebase Auth                        |
| `email`              | String    | Firebase Auth                               |
| `name`               | String    | preenchido no cadastro                      |
| `registrationNumber` | String    | matrícula — preenchida no cadastro          |
| `team`               | String    | equipe/setor — preenchida no cadastro       |
| `shift`              | enum      | `MANHA`, `TARDE` ou `NOITE`                 |
| `createdAt`          | Instant   | timestamp do signup                         |

### 1.3 Turno (shift) ✅ (PR #8)
Obrigatório no cadastro. Define a quota de trocas (seção 3). Pode ser
`MANHA`, `TARDE` ou `NOITE`. Perfis legados sem turno assumem `MANHA` como
fallback (leitura defensiva no Firestore).

## 2. Dias de trabalho

> **Terminologia (atualizada):** o que o app internamente chama de `Folga`
> é exibido na UI como **"dia de trabalho"**. Quando o usuário cadastra um
> dia, ele está marcando uma data de trabalho que **quer trocar com alguém**
> — ou seja, pedir que outro colega trabalhe no lugar dele naquele dia. O
> domínio/persistência (`Folga`, `folgas/{id}`) mantém o nome antigo para
> evitar reescrever dados existentes no Firestore.

### 2.1 Cadastrar dia de trabalho ✅ (PR #1, PR #39, PR #41)
Usuário escolhe uma data via calendário (Material3 DatePicker, formato
visual DD/MM/AAAA) e opcionalmente uma observação. O dia é criado em
`folgas/{id}` com:

- `userId`: quem cadastrou
- `date`: data escolhida
- `status`: `SCHEDULED`
- `note`: observação opcional

**Validações no cadastro:**
- **Data mínima D+1** (PR #39): só é possível cadastrar a partir de
  amanhã. O DatePicker desabilita visualmente datas anteriores e o
  `FolgasViewModel.reserve()` rejeita com a mensagem _"Selecione uma
  data a partir de amanhã."_ Evita cadastrar retroativo ou "hoje" (dia
  já em curso).
- **Sem duplicata na mesma data** (PR #41): se o usuário já tem uma
  folga ativa (`status != CANCELLED`) na data escolhida, o `reserve()`
  bloqueia com a mensagem _"Dia de trabalho já registrado."_ Evita o
  mesmo dia aparecer 2× na lista.
- **Sem troca aceita na mesma data** (PR #51): se o usuário já tem uma
  troca `ACCEPTED` envolvendo a data escolhida (como requester ou
  target — o caso típico é o requester que cedeu o dia: a folga muda
  de dono pro target e some da listagem dele), o `reserve()` bloqueia
  com a mensagem _"Você já tem uma troca agendada para a data
  informada."_ Evita compromisso duplicado quando a folga em si não
  aparece mais em "Meus dias cadastrados".

### 2.2 Status
- `SCHEDULED` — cadastrada, ainda válida
- `SWAPPED` — trocada com outro usuário (dono virou o outro)
- `CANCELLED` — cancelada pelo próprio dono
- `COMPLETED` — reservado para uso futuro (marcar dia passado)

### 2.3 Cancelar dia cadastrado ✅ (PR #1, PR #3)
Só o dono do dia pode cancelar. Só é possível cancelar dias em
`SCHEDULED`. A operação é transacional no Firestore para evitar corrida
com `accept()` de uma troca que estava mirando nesse dia.

### 2.4 Marcação visual "Aguardando" em dia com solicitação em aberto ✅ (PR #41, PR #52)
Na tela **Trocar dia de trabalho**, na lista "Meus dias cadastrados":
se o dia está como `fromFolgaId` de uma troca `PENDING` aberta pelo
usuário, a chip recebe o sufixo " · Aguardando" em laranja e fica
`enabled = false` (não é mais selecionável). Evita o usuário tentar
abrir uma 2ª troca pra um dia que já tem solicitação em aberto.

Pra reforçar (defesa em profundidade), `SwapsViewModel.requestSwap()`
rejeita a submissão se a folga selecionada estiver no
`folgaIdsAwaiting.value` (PR #42, PR #52), com a mensagem _"Esse dia já tem
uma solicitação aguardando."_ Cobre o caso onde o estado da UI estava stale
(ex.: `outgoing` ainda não carregou do Firestore).

## 3. Trocas de dia de trabalho

### 3.1 Fluxo geral ✅ (unidirecional)
Quando **A** solicita uma troca com **B**, o pedido é: _"B, trabalhe no
meu lugar no dia X."_ Não há dia de contrapartida — o colega escolhido
apenas assume o dia que o solicitante cadastrou.

1. **A** (requester) escolhe um dia seu cadastrado (dia que A não quer
   mais trabalhar).
2. **A** escolhe um **colega B** da lista de usuários (escolhe a pessoa,
   não um dia específico dela).
3. **A** envia a solicitação. Status inicial: `PENDING`.
4. **B** pode **Aceitar**, **Recusar** ou **A** pode **Cancelar**,
   desde que a troca ainda esteja `PENDING`.
5. Ao aceitar: em uma transação Firestore, o dia **muda de dono** para
   B e o status vira `SWAPPED`. A sai do compromisso; B assume.

**Implicações do modelo unidirecional:**
- Só o solicitante "paga" o dia. O colega ganha um compromisso a mais
  sem precisar oferecer nada em contrapartida.
- Não existe checagem de disponibilidade do colega — ele pode recusar
  se não puder assumir.
- Só existe **uma** data envolvida em cada troca (a do solicitante).
  A tela inicial mostra "A → B · DD/MM/AAAA".
- Trocas antigas do modelo bidirecional (com `toFolgaId` preenchido)
  continuam legíveis no Firestore — o campo é ignorado e apenas a
  folga do solicitante é considerada.

### 3.2 Quota de trocas por período ✅ (PR #9, PR #37)

> **Regra:** cada usuário tem um número máximo de trocas *consumidoras de
> quota* que ele pode ter **iniciado** dentro de um período de contagem.

| Turno       | Quota por período |
| ----------- | ----------------- |
| `MANHA`     | 4                 |
| `TARDE`     | 4                 |
| `NOITE`     | 3                 |

**O que conta (atualizado em PR #37):**
- **Trocas `PENDING` e `ACCEPTED`** consomem quota — a solicitação aguardando
  já ocupa a vaga até ser resolvido. `REJECTED` e `CANCELLED` **não**
  consomem.
- **Só quem iniciou** (o `requesterId`). O alvo (`targetId`) **não**
  consome quota da própria conta — mesmo aceitando uma troca, o alvo pode
  iniciar livremente as próprias trocas no período.

**Período de contagem:** do **dia 16** do mês até o **dia 15** do mês
seguinte, inclusive nas duas pontas. Exemplo: hoje é 20/abr/2026 → o
período corrente é 16/abr/2026 a 15/mai/2026.

A troca é "posicionada" no período pelo `respondedAt` (momento em que
virou `ACCEPTED`). Para trocas antigas que não tenham esse timestamp, cai
pra `createdAt` como fallback.

**Comportamento na UI:**
- Tela **Trocar dia de trabalho** exibe chip com `Trocas restantes: X de Y
  · Turno`. A chip fica com cor de erro (vermelha) quando o limite é
  atingido.
- Quando o usuário atinge o limite, o botão **Solicitar troca** fica
  desabilitado e mostra o texto "Limite de trocas atingido no período".
- `SwapsViewModel.requestSwap()` também rejeita a submissão quando a quota
  está batida (defesa-em-profundidade — cobre race condition onde o
  estado da UI está desatualizado).
- A quota agora **bloqueia** a solicitação (antes era aviso
  não-bloqueante).

### 3.3 Validação de conflito do alvo ✅ (PR #37, PR #39, PR #40)

> **Regra:** um colega não pode aceitar uma troca pra um dia em que ele
> já tem outro compromisso. O app valida isso na hora de abrir a troca
> (não deixa nem submeter).

**O que bloqueia uma solicitação A→B pra a data X:**
- **Folga ativa de B em X** (`status != CANCELLED` — inclui SCHEDULED
  e SWAPPED). Se B já tem o próprio dia cadastrado nessa data ou já
  assumiu via outra troca aceita, ele não pode aceitar uma 2ª troca
  pro mesmo dia.
- **Troca `PENDING` ou `ACCEPTED` envolvendo B em X** (B como
  requester ou target). `PENDING` é compromisso vivo aguardando
  aceite; `ACCEPTED` já está confirmado. `REJECTED` e `CANCELLED`
  **não** contam — recusa não é compromisso real, e cancelada foi
  desfeita explicitamente (PR #40 ajustou esse comportamento).

Mensagem exibida: _"O colega selecionado já tem um agendamento ou
troca em aberto para essa data e não pode assumir a solicitação."_

A validação é feita no `SwapsViewModel.requestSwap()` antes de chamar
o backend. Não está nas Firestore rules (precisaria de query de
coleção, não suportado em rules) — o gate é só client-side.

### 3.4 Restrição por grupo de turno ✅ (PR #23)

> **Regra:** só é possível trocar com colegas do mesmo **grupo de turno**.
> `MANHA` e `TARDE` formam o grupo **diurno** (podem trocar entre si);
> `NOITE` forma o grupo **noturno** (só troca com `NOITE`).

**Motivo:** a rotina do noturno é incompatível com a do diurno. Permitir
que um diurno assuma um plantão noturno (ou vice-versa) via troca quebra
a escala real de trabalho.

**Onde é aplicada:**
- **UI (SwapsScreen):** o picker **"Escolha um colega"** só mostra
  colegas do mesmo grupo de turno do usuário logado. Aparece uma linha
  explicativa acima da lista. Se não houver nenhum colega compatível, a
  mensagem padrão "Nenhum colega disponível" é exibida.
- **ViewModel (SwapsViewModel):** `colleagues` é filtrado com
  `me.shift.isCompatibleWith(it.shift)`; `requestSwap()` reforça a
  validação antes de chamar o backend.
- **Backend (firestore.rules):** a regra de `allow create` em
  `/swaps/{id}` exige `shiftsCompatible(requesterShift, targetShift)`
  antes de aceitar o doc. Isso bloqueia mesmo um cliente malicioso que
  contorne a UI.

### 3.5 Aceitar troca + auto-recusa de irmãs ✅ (PR #46)

> **Regra:** quando o usuário aceita uma troca, qualquer outra troca
> recebida (`incoming`) `PENDING` apontando para a **mesma data** é
> automaticamente recusada — o usuário não pode trabalhar dois turnos
> no mesmo dia.

Implementado em `SwapsViewModel.accept(swapId)`:
1. Snapshot da troca aceita **antes** do commit (depois do `accept`,
   o `status` vira `ACCEPTED` e a troca cairia fora do filtro
   `PENDING`).
2. Resolve a `conflictDate` via `allFolgas` + `fromFolgaId`.
3. Após o `accept` rodar com sucesso, percorre `incoming` e chama
   `swapRepository.reject(siblingId)` em cada troca aguardando que cai
   na mesma data.
4. Idempotência: o guard `PENDING` dentro do `resolvePending` no
   Firestore garante que se o requester cancelar primeiro, o
   `reject` é no-op.

A ação é silenciosa — não há prompt nem mensagem. As trocas
auto-recusadas aparecem como `REJECTED` na lista do solicitante.

### 3.6 Filtros de status nas listas ✅ (PR #46, PR #51, PR #52)

A tela **Trocar dia de trabalho** tem um filtro multi-select por
status (compartilhado entre Recebidas e Enviadas) com chips: `Aguardando`,
`Confirmada`, `Recusada`, `Cancelada`.

- Default: todos selecionados (mesma listagem de antes do filtro).
- Não deixamos zerar a seleção (sem nenhum status as listas ficariam
  vazias). O botão **Limpar filtros** restaura o default.
- Estado é mantido só durante a sessão da tela
  (`rememberSaveable` cobre rotação/recomposição, não persiste
  entre navegações).
- O label "Pendente" foi renomeado para **"Aguardando"** em PR #52 para
  manter a consistência com o restante do sistema.
- O label "Aceita" foi renomeado para **"Confirmada"** em PR #51 pra
  alinhar com o badge verde mostrado no card.

### 3.7 Copy dos cards de troca ✅ (PR #51)

A linha descritiva de cada `ShiftSwapCard` é montada por
`swapDescription(status, requesterName, targetName, date, viewerRole)`
em `composeApp/src/commonMain/kotlin/app/folga/ui/common/ShiftSwapCard.kt`.

`viewerRole` define a perspectiva do usuário olhando o card:
- `REQUESTER` — quando o viewer é quem **iniciou** a troca (Enviadas;
  ou Home se `iAmRequester`).
- `TARGET` — quando o viewer é quem **recebeu** o pedido (Recebidas;
  ou Home se não-requester).
- `null` — fallback neutro (3ª pessoa) usado em listagens admin.

| Status     | Perspectiva REQUESTER                                    | Perspectiva TARGET                                         |
| ---------- | -------------------------------------------------------- | ---------------------------------------------------------- |
| PENDING    | _Aguardando o usuário **\<target\>**_                    | _Aguardando sua resposta — **\<requester\>** pediu o dia X_ |
| ACCEPTED   | _**\<target\>** aceitou trabalhar para você no dia X_    | _Você aceitou trabalhar para **\<requester\>** no dia X_   |
| REJECTED   | _Proposta recusada por **\<target\>** no dia X_          | _Você recusou a proposta de **\<requester\>** no dia X_    |
| CANCELLED  | _Você cancelou o trabalho agendado (X)_                  | _**\<requester\>** cancelou o trabalho agendado (X)_       |

`CANCELLED` só é alcançável pelo requester (regra de negócio +
Firestore rules), então a perspectiva é determinística.

### 3.8 Regra de 2 plantões seguidos (NOITE) 🚧 (pendente)

> **Regra pretendida:** usuários do turno `NOITE` não podem trabalhar mais
> de 2 noites seguidas.

**Status:** adiada. Para aplicar essa regra o app precisa saber quando
cada usuário está de plantão, o que depende de uma **escala de plantão**
que ainda não está modelada no sistema. Hoje o app só conhece os dias de
trabalho que o usuário cadastrou para trocar — qualquer checagem seria
uma aproximação que pode gerar falso-positivo.

**Próximo passo:** definir como cadastrar a escala (ex.: cada usuário tem
um turno semanal fixo? Escala 12x36? Calendário por colaborador?). Depois
implementamos em um PR separado, aplicando a regra tanto no cadastro de
dia quanto na aceitação de troca.

## 4. Administração ✅ (PR #10)

### 4.1 Perfis Admin vs Usuário ✅ (PR #10)
- `role = ADMIN`: tem acesso a tudo, incluindo tela de Administração.
- `role = USER`: acesso apenas às próprias folgas e trocas (default para
  qualquer novo cadastro).

Armazenado em `users/{uid}.role` no Firestore. Docs antigos sem o campo
são lidos como `USER` (princípio do menor privilégio).

**Primeiro admin — lista hardcoded (`AdminBootstrap`):**
- `davi.schneid@gmail.com`
- `janaina.flor@gmail.com`

Qualquer conta criada (email/senha ou Google Sign-In) com um e-mail dessa
lista recebe `role = ADMIN` automaticamente no primeiro perfil criado.
Essas contas também passam pela whitelist de e-mails mesmo que a coleção
`allowed_emails` esteja vazia — resolve o chicken-and-egg do primeiro
admin. Para trocar quem é admin bootstrap, editar `AdminBootstrap.kt` e
publicar nova versão.

### 4.2 Tela de administração ✅ (PR #10)

Botão **Admin** aparece na TopAppBar da tela **Meus dias de trabalho** só
quando `currentUser.role == ADMIN` — usuário comum não vê a porta de
entrada.

A tela tem duas abas:

**Aba Usuários**
- Lista todos os usuários cadastrados com nome, e-mail e role atual.
- Botão **Tornar admin / Tornar usuário** por linha.
- Bloqueios:
  - Admin não consegue alterar a própria role (evita auto-trancar).
  - Admin bootstrap não pode ser despromovido (o bootstrap voltaria a
    promover no próximo login — esconder o botão evita confusão).

**Aba E-mails autorizados**
- Lista os e-mails da coleção `allowed_emails` + os admins bootstrap
  (sempre autorizados, mostrados acima pra transparência).
- Campo pra adicionar novo e-mail (validação simples de formato + trim +
  lowercase). O registro grava quem autorizou (`addedBy`) e quando.
- Remover e-mail deleta o doc; admins bootstrap não podem ser removidos
  (o botão de lixeira é escondido nessa linha).

### 4.3 Whitelist de e-mails ✅ (PR #10)

Coleção `allowed_emails/{email_normalizado}` no Firestore (docId é o
próprio e-mail em lowercase). Permite checagem O(1) no gate de auth sem
precisar de query com `where`.

**Onde o gate é aplicado** (em `FirebaseAuthRepository`):
1. `signUpWithEmail` — antes de criar a conta no Firebase Auth (evita
   conta órfã se o e-mail não estiver autorizado).
2. `signInWithEmail` — antes de autenticar (bloqueia usuário cujo e-mail
   foi revogado da whitelist, mesmo com conta no Firebase Auth).
3. `signInWithGoogleIdToken` — depois de validar o ID token com o Google
   (precisa saber o e-mail real) mas antes de gravar qualquer perfil;
   se rejeitar, faz `signOut` do Firebase na sequência.

Mensagem de erro padronizada: _"Não autorizado. Solicite ao administrador
que libere este e-mail."_

Admins bootstrap passam direto pelo gate mesmo se não estiverem na
coleção `allowed_emails` (veja seção 4.1).

### 4.4 Regras do Firestore (recomendação) 🚧

O código do app já bloqueia alteração de role e whitelist pela UI (só
admin enxerga as telas/ações). Pra defesa-em-profundidade contra escrita
direta via SDK, aplicar no console em **Firestore Database → Regras**:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    function isSignedIn() { return request.auth != null; }
    function isAdmin() {
      return isSignedIn() &&
        get(/databases/$(database)/documents/users/$(request.auth.uid)).data.role == 'ADMIN';
    }

    match /users/{uid} {
      allow read: if isSignedIn();
      // Usuário pode editar o próprio doc, mas NÃO pode mudar o campo
      // role (exceto admin). Criação livre só valida que role começa
      // como USER — admin é atribuído pelo backend via bootstrap.
      allow create: if isSignedIn() &&
        request.auth.uid == uid &&
        (!('role' in request.resource.data) || request.resource.data.role == 'USER');
      allow update: if isSignedIn() && (
        isAdmin() ||
        (request.auth.uid == uid &&
         request.resource.data.role == resource.data.role)
      );
      allow delete: if isAdmin();
    }

    match /allowed_emails/{email} {
      allow read: if isSignedIn();
      allow write: if isAdmin();
    }

    match /folgas/{id} {
      allow read: if isSignedIn();
      allow write: if isSignedIn() &&
        (isAdmin() || request.auth.uid == resource.data.userId);
      allow create: if isSignedIn() &&
        request.auth.uid == request.resource.data.userId;
    }

    match /swaps/{id} {
      allow read: if isSignedIn();
      allow create: if isSignedIn() &&
        request.auth.uid == request.resource.data.requesterId;
      allow update: if isSignedIn() && (
        isAdmin() ||
        request.auth.uid == resource.data.requesterId ||
        request.auth.uid == resource.data.targetId
      );
    }
  }
}
```

> **Observação:** o admin bootstrap precisa estar na lista
> `AdminBootstrap.ADMIN_EMAILS` **e** ter feito o primeiro login com o
> app pra que o `users/{uid}.role` vire `ADMIN` no Firestore. Só depois
> que esse doc existe é que `isAdmin()` nas regras passa a retornar
> `true`.

### 4.5 Logout estável ✅ (PR #46, PR #51)

O botão **Sair** fica em **Perfil → Lista de atalhos**. Ao clicar:

1. O `App.kt` (em `rememberCoroutineScope()`) chama
   `authRepository.signOut()` — escopo estável, **não** o
   `viewModelScope` do `ProfileViewModel`.
2. `signOut` limpa o `fcmToken` em `users/{uid}` (pra não vazar
   notificação pra ex-usuário do device) e chama
   `FirebaseAuth.signOut()`.
3. `manualUser.value = null` → `currentUser` vira `null`.
4. A auto-redirect em `App.kt` (`!loggedIn && screen !is Login &&
   screen !is Register`) leva o usuário pra Login.

**Por que não setar `screen = Login` direto no callback do botão?**
Se a navegação acontecesse antes do `currentUser` virar `null`, o
bloco `if (loggedIn && profileComplete && screen is Login) screen =
Folgas` (logo abaixo na auto-redirect) bumparia o usuário de volta
pra Folgas — efeito de "flicker" reportado no PR #46/#51.

**Por que `viewModelScope` não funcionava?** O ProfileViewModel é
destruído quando o `ProfileScreen` sai do Composable. Se o `signOut`
estava launched no `viewModelScope`, ele era cancelado no meio —
`auth.signOut()` nunca rodava e o `currentUser` ficava não-null.

## 5. Notificações push ✅ (PR #33, PR #34)

Quando uma troca é criada (`/swaps/{id}` com `status = PENDING`), uma
**Cloud Function** (`functions/index.js`) dispara uma notificação via
Firebase Cloud Messaging (FCM) pro device do `targetId`. O token FCM
do dispositivo é gravado em `users/{uid}.fcmToken` no signin (Android
via `FcmTokenSyncer` + `FolgaMessagingService`) e limpo no signout pra
não notificar usuário deslogado.

No iOS o stub está no-op — push real só funciona em Android hoje.

## 6. UX e ordenação de listas ✅ (PR #43, PR #44)

Todas as listas com data são ordenadas por data ascendente (próxima
a acontecer primeiro):

- Home → **Trocas agendadas** (`FolgasViewModel.scheduledSwaps`)
- Home → **Meus dias cadastrados** (`FolgasScreen.myScheduled`)
- Trocas → **Meus dias cadastrados** (chips de seleção)
- Trocas → **Recebidas** / **Enviadas**

O **status badge** (`StatusBadge`) usa cores fixas:
`AGUARDANDO` laranja, `CONFIRMADA` verde, `RECUSADA` vermelha,
`CANCELADA` cinza. Reduzido em PR #43 pra ocupar menos altura no
rodapé do card de troca.

## 7. Telemetria e dados

Tudo persistido no Firestore (`users`, `folgas`, `swaps`,
`allowed_emails`). O Firestore tem persistência offline nativa no
Android e iOS — o app funciona sem rede e sincroniza quando voltar.

## 8. Suporte ao usuário — perguntas frequentes

### "Por que meu botão de Solicitar troca está desabilitado?"
Você atingiu a quota de trocas no período corrente (dia 16 ao dia 15).
Desde o PR #37, trocas `PENDING` (Aguardando) e `ACCEPTED` (Confirmada)
consumem quota. O botão volta a ficar disponível quando:
- Começa um novo período (dia 16).
- Uma das suas solicitações **Aguardando** é recusada/cancelada (libera vaga
  imediatamente).
- Uma troca `ACCEPTED` é revertida (hoje o app não suporta cancelar
  troca já aceita — então, na prática, só com a virada do período).

### "Meu dia cadastrado sumiu depois que o colega aceitou a troca — é bug?"
Não — é o comportamento esperado no modelo unidirecional. Quando você
pede uma troca, você está pedindo que o colega **trabalhe no seu lugar**
naquele dia. Ao aceitar, o dia passa a ser dele (status `SWAPPED`) e
some da sua lista "Meus dias cadastrados". Ele continua visível na
seção **Trocas agendadas** da tela inicial e também nas abas
**Enviadas** / **Recebidas** da tela de trocas.

### "Não consigo entrar com minha conta Google — ela diz não autorizada."
O admin ainda não adicionou seu e-mail à whitelist. Peça para ele incluir
o seu e-mail na tela **Administração → E-mails autorizados**.

### "Fiz login e não vejo o botão Admin."
Seu perfil é `USER`. Peça pra um admin bootstrap te promover em
**Administração → Usuários**, ou (se você for admin bootstrap) confira se
fez login pela primeira vez com um dos e-mails listados em
`AdminBootstrap.ADMIN_EMAILS`.

### "Por que preciso informar turno no cadastro?"
O turno define quantas trocas por período você pode fazer e — no futuro —
a regra de plantões seguidos do noturno.

### "Tentei cadastrar um dia e o app disse 'Dia de trabalho já registrado'."
Você já tem uma folga ativa nessa data (SCHEDULED ou SWAPPED). Cancele
a existente em **Meus dias cadastrados** ou escolha outra data. Datas
em `CANCELLED` não bloqueiam novo cadastro.

### "Não consigo selecionar a data de hoje pra cadastrar."
É por desenho — o cadastro só permite a partir de amanhã (D+1). Não
dá pra trocar o turno de um dia que já está em curso.

### "O chip de um dia ficou cinza com 'Aguardando' do lado."
Esse dia já tem uma solicitação aguardando que você abriu. Aguarde o colega
aceitar/recusar (ou cancele a troca em **Enviadas**) pra liberar o dia
pra uma nova solicitação.

### "Por que minha quota baixou se a troca ainda está aguardando?"
Desde o PR #37, trocas `PENDING` também consomem quota — o status
Aguardando já ocupa a vaga até ser resolvido. Se for recusada/cancelada
a vaga volta, se for aceita continua consumindo.

### "Esqueci minha senha — como redefino?"
Na tela de Login tem o link **"Esqueci minha senha"** abaixo do
botão de entrar. Informe o e-mail; se ele estiver autorizado, o
Firebase manda um link de redefinição na sua caixa. Confira o
spam/lixo eletrônico. Se o e-mail não chegou, peça ao admin pra
confirmar se está na lista de e-mails autorizados.

### "Aceitei uma troca e outra solicitação que eu tinha foi marcada como Recusada — por quê?"
Desde o PR #46, ao aceitar uma troca o app **automaticamente recusa**
qualquer outra solicitação `PENDING` (Aguardando) que você tenha recebido pra a
**mesma data** — você não pode trabalhar dois turnos no mesmo dia. As
trocas afetadas aparecem como `RECUSADA` na lista do solicitante; ele
pode tentar com outro colega.

### "Tentei cadastrar um dia e o app disse 'Você já tem uma troca agendada para a data informada'."
Você tem uma troca já **aceita** (status `Confirmada`) envolvendo essa
data — como solicitante ou como colega que assumiu. Cadastrar um novo
dia geraria compromisso duplicado. Confira a seção **Trocas agendadas**
na tela inicial.

### "Saí (Sair) e o app voltou pra Home em vez da tela de Login."
Era um bug do `signOut` rodando em escopo de tela; corrigido nos PRs
#46/#51. Se acontecer de novo, force o fechamento do app pelas
configurações do Android — provavelmente o build do device é antigo.

---

Última atualização: PRs #46 (auto-recusa de irmãs no aceite, filtros
de status, logout volta pra Login), #47 (redesign Login), #49/#50
(esqueci minha senha com gate por `allowed_emails`), #51 (filtro
"Confirmada", bloqueio de cadastro em data com troca aceita, copy
dos cards sensível ao status + perspectiva, logout estável), #52
(Refinar terminologia 'Aguardando' e label do filtro de status).
