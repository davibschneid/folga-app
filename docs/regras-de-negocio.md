# Regras de negócio — Easy Folgas

Documento vivo com as regras de negócio do app, pensado como referência para
suporte ao usuário e para o time de desenvolvimento. Cada seção indica o
status da regra (✅ implementada, 🚧 pendente, 💡 planejada) e o PR onde ela
entrou (quando aplicável).

## 1. Cadastro e perfil de usuário

### 1.1 Login ✅ (PR #2, PR #8)
- Permitido apenas via **e-mail/senha** (Firebase Auth) ou **Google Sign-In**.
- Após o sign-up com Google, o usuário é direcionado à tela **Completar
  cadastro** para preencher dados que o Google não fornece (matrícula,
  equipe, turno).
- Autenticação fica em `Authentication → Users` no Firebase Console.

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

## 2. Folgas

### 2.1 Reservar folga ✅ (PR #1)
Usuário escolhe uma data via calendário (Material3 DatePicker, formato
visual DD/MM/AAAA) e opcionalmente uma observação. A folga é criada em
`folgas/{id}` com:

- `userId`: quem reservou
- `date`: data escolhida
- `status`: `SCHEDULED`
- `note`: observação opcional

### 2.2 Status da folga
- `SCHEDULED` — reservada, ainda válida
- `SWAPPED` — trocada com outro usuário (dono virou o outro)
- `CANCELLED` — cancelada pelo próprio dono
- `COMPLETED` — reservado para uso futuro (marcar folga passada)

### 2.3 Cancelar folga ✅ (PR #1, PR #3)
Só o dono da folga pode cancelar. Só é possível cancelar folgas em
`SCHEDULED`. A operação é transacional no Firestore para evitar corrida
com `accept()` de uma troca que estava mirando nessa folga.

## 3. Trocas de folga

### 3.1 Fluxo geral ✅ (PR #1, PR #3)
Quando **A** solicita uma troca com **B**, o pedido é: _"B, trabalhe no
meu lugar no dia da minha folga. Em troca, eu trabalho no seu lugar no
dia da sua folga."_

1. **A** (requester) escolhe uma folga sua (dia em que A está de folga
   originalmente) e uma folga de **B** (dia em que B está de folga
   originalmente).
2. **A** solicita troca. Status inicial: `PENDING`.
3. **B** pode **Aceitar**, **Recusar** ou **A** pode **Cancelar**,
   desde que a troca ainda esteja `PENDING`.
4. Ao aceitar: as duas folgas trocam de dono em uma transação Firestore.
   A folga de A vira de B (B agora fica de folga nesse dia e A trabalha),
   a folga de B vira de A (A fica de folga nesse dia e B trabalha).
   Ambas passam a `SWAPPED`.

### 3.2 Quota de trocas por período ✅ (PR #9)

> **Regra:** cada usuário tem um número máximo de trocas *aceitas* que ele
> pode ter **iniciado** dentro de um período de contagem.

| Turno       | Quota por período |
| ----------- | ----------------- |
| `MANHA`     | 4                 |
| `TARDE`     | 4                 |
| `NOITE`     | 3                 |

**O que conta:**
- **Só trocas aceitas.** Trocas `PENDING`, `REJECTED` ou `CANCELLED` **não**
  consomem quota.
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
- Tela **Trocas** exibe chip com `Trocas no período: N/Quota · Turno`.
- Ao clicar **Solicitar troca** com quota já atingida, aparece um
  `AlertDialog` de aviso. O usuário pode **Continuar** (segue com a
  solicitação mesmo assim) ou **Cancelar** (volta sem enviar).
- A quota nunca bloqueia a solicitação — é só aviso preventivo, para que o
  usuário saiba que aquela troca vai passar do limite se o colega aceitar.

### 3.3 Regra de 2 plantões seguidos (NOITE) 🚧 (pendente)

> **Regra pretendida:** usuários do turno `NOITE` não podem trabalhar mais
> de 2 noites seguidas.

**Status:** adiada. Para aplicar essa regra o app precisa saber quando
cada usuário está de plantão, o que depende de uma **escala de plantão**
que ainda não está modelada no sistema. Hoje o app só conhece as *folgas*,
não os dias de trabalho — qualquer checagem seria uma aproximação que
pode gerar falso-positivo.

**Próximo passo:** definir como cadastrar a escala (ex.: cada usuário tem
um turno semanal fixo? Escala 12x36? Calendário por colaborador?). Depois
implementamos em um PR separado, aplicando a regra tanto na reserva de
folga quanto na aceitação de troca.

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

Botão **Admin** aparece na TopAppBar da tela **Minhas Folgas** só quando
`currentUser.role == ADMIN` — usuário comum não vê a porta de entrada.

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

## 5. Telemetria e dados

Tudo persistido no Firestore (`users`, `folgas`, `swaps`). O Firestore tem
persistência offline nativa no Android e iOS — o app funciona sem rede e
sincroniza quando voltar.

## 6. Suporte ao usuário — perguntas frequentes

### "Por que meu botão de Solicitar troca está mostrando um aviso?"
Você já atingiu a quota de trocas aceitas no período corrente (16→15).
O aviso é preventivo — se a troca for aceita, você passa do limite. Você
pode confirmar e seguir, ou cancelar.

### "Minha folga sumiu depois que o colega aceitou a troca — é bug?"
Não — isso é o comportamento esperado. Quando você pede uma troca, você
está pedindo que o colega **trabalhe no seu lugar** naquele dia: a sua
folga original passa pra ele, e a folga dele passa pra você. As duas
folgas ficam com status `SWAPPED` e aparecem no histórico nas abas
**Enviadas** / **Recebidas** da tela de Trocas.

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

---

Última atualização: PR #10 (admin + whitelist de e-mails).
