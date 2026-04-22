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
1. **A** (requester) escolhe uma folga sua e uma folga de **B** (target).
2. **A** solicita troca. Status inicial: `PENDING`.
3. **B** pode **Aceitar**, **Recusar** ou **A** pode **Cancelar**, desde que
   a troca ainda esteja `PENDING`.
4. Ao aceitar: as duas folgas trocam de dono em uma transação Firestore
   (`from` vira de `B`, `to` vira de `A`, ambas passam a `SWAPPED`).

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

## 4. Administração 💡 (PR #10 — futuro)

### 4.1 Perfis Admin vs Usuário
- `role = admin`: tem acesso a tudo, incluindo tela de administração.
- `role = user`: acesso apenas às próprias folgas e trocas.

Primeiro admin é definido por **lista hardcoded de e-mails** — qualquer
conta cadastrada com um e-mail nessa lista entra direto como admin.

### 4.2 Tela de administração
- Lista todos os usuários do sistema.
- Permite alternar `role` de cada um (Admin ↔ Usuário).
- Regras do Firestore garantem que só admin pode alterar `role` — cliente
  normal não consegue escalar privilégio via API direta.

### 4.3 Whitelist de e-mails
Coleção `allowed_emails/{emailHash}` no Firestore com e-mails autorizados.
Só esses e-mails conseguem concluir o cadastro (por email/senha ou
Google Sign-In). Se alguém tentar logar com Google e o e-mail não estiver
na whitelist, o app desloga a conta e mostra mensagem de não-autorizado.
Tela de admin permite adicionar/remover e-mails da whitelist.

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
Não. Ela foi transferida para o colega (`SWAPPED`). Você ganhou a folga
dele em troca. O histórico aparece nas abas **Enviadas** / **Recebidas**
da tela de Trocas.

### "Não consigo entrar com minha conta Google — ela diz não autorizada."
(Após PR #10) O admin ainda não adicionou seu e-mail à whitelist. Peça
para ele incluir o seu e-mail na tela **Administração → E-mails
autorizados**.

### "Por que preciso informar turno no cadastro?"
O turno define quantas trocas por período você pode fazer e — no futuro —
a regra de plantões seguidos do noturno.

---

Última atualização: PR #9 (quota de trocas).
