/**
 * Cloud Functions do Easy Folgas.
 *
 * `onAllowedEmailCreated`:
 *   - Trigger: criação de doc em `allowed_emails/{emailId}`.
 *   - Lê o e-mail (campo `email` ou docId, normalizado lowercase) e
 *     escreve um doc em `mail/` com o conteúdo de boas-vindas. A
 *     extensão "Trigger Email from Firestore" (instalada via console
 *     pelo admin) consome essa coleção e dispara o e-mail via SMTP
 *     configurado na extensão. Mantém a lógica de envio fora do
 *     nosso código (sem credenciais SMTP versionadas).
 *
 * `onSwapCreated`:
 *   - Trigger: criação de doc em `swaps/{swapId}`.
 *   - Lê o `targetId` (quem recebeu o pedido) e o `requesterId`
 *     (quem fez o pedido) do doc criado.
 *   - Busca `users/{targetId}.fcmToken` e o nome do requester pra
 *     compor o título/corpo da notificação.
 *   - Envia FCM via Admin SDK pro token do target.
 *   - Se a troca já é criada com status diferente de PENDING (não
 *     deveria acontecer pelo app, mas defensivo), pula o envio.
 *
 * Por que 2nd gen (`onDocumentCreated`):
 *  - cold-start menor;
 *  - assinatura tipada;
 *  - segue o padrão recomendado pela Firebase desde 2024.
 */
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { logger } = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

const db = admin.firestore();
const messaging = admin.messaging();

exports.onAllowedEmailCreated = onDocumentCreated(
  // Mesma região do `onSwapCreated` pra simplificar — Firestore deste
  // projeto roda em us-central1.
  { document: "allowed_emails/{emailId}", region: "us-central1" },
  async (event) => {
    const snap = event.data;
    if (!snap) {
      logger.warn("allowed_email criado sem snapshot");
      return;
    }
    const data = snap.data() || {};
    // O docId já é o e-mail normalizado (lowercase + trim) —
    // FirestoreAllowedEmailRepository usa `AdminBootstrap.normalize`
    // antes do `set`. Cair no campo `email` é defesa em
    // profundidade (caso futuro mude a chave).
    const email = (data.email || event.params.emailId || "").toString().trim();
    if (!email || !email.includes("@")) {
      logger.warn(`allowed_email ${event.params.emailId} sem e-mail válido, pulando boas-vindas`);
      return;
    }

    // Idempotência: se a função reexecutar (retry, replay), não
    // mandamos o e-mail duas vezes. Usamos o próprio docId em
    // `mail/` igual ao do `allowed_emails/` — `set` com merge=false
    // é write-once.
    const mailDocId = `welcome-${event.params.emailId}`;
    const mailRef = db.collection("mail").doc(mailDocId);
    const existing = await mailRef.get();
    if (existing.exists) {
      logger.info(`E-mail de boas-vindas já enfileirado pra ${email}, pulando`);
      return;
    }

    const subject = "Bem-vindo ao easyshift!";
    // Texto solicitado pelo cliente. Mantemos versão `text` (plain)
    // pra clientes que não renderizam HTML, e `html` simples
    // só com quebras de linha — sem branding pesado pra não
    // depender de hosting de assets.
    //
    // O link da Play Store vem de `config/welcomeEmail.playStoreUrl`
    // pra que o admin consiga atualizar pelo console (ex.: depois
    // que a app for publicada com package id final) sem redeploy
    // de função nem release nova de app. Se o doc não existir ou
    // o campo estiver vazio, cai no fallback.
    const DEFAULT_PLAY_STORE_URL =
      "https://play.google.com/store/apps/details?id=app.folga.android";
    let playStoreUrl = DEFAULT_PLAY_STORE_URL;
    try {
      const cfgSnap = await db.collection("config").doc("welcomeEmail").get();
      const cfgUrl = cfgSnap.exists && cfgSnap.data() && cfgSnap.data().playStoreUrl;
      if (cfgUrl && typeof cfgUrl === "string" && cfgUrl.trim()) {
        playStoreUrl = cfgUrl.trim();
      }
    } catch (err) {
      // Não bloqueia envio do e-mail por falha no config — só loga
      // e segue com o default. Próxima execução tenta de novo.
      logger.warn("Falha lendo config/welcomeEmail, usando URL default", err);
    }
    const text = [
      "Olá!",
      "",
      "Seu e-mail foi adicionado ao easyshift, seja muito bem-vindo!",
      "Para começar a usar o app, basta baixá-lo e concluir seu cadastro.",
      "",
      "Baixe na Play Store:",
      `👉 ${playStoreUrl}`,
      "",
      "Qualquer dúvida, é só chamar. Boa experiência no easyshift! 🚀",
    ].join("\n");
    const html = [
      "<p>Olá!</p>",
      "<p>Seu e&#8209;mail foi adicionado ao <strong>easyshift</strong>, seja muito bem&#8209;vindo!<br>",
      "Para começar a usar o app, basta baixá&#8209;lo e concluir seu cadastro.</p>",
      "<p>Baixe na Play Store:<br>",
      `👉 <a href="${playStoreUrl}">${playStoreUrl}</a></p>`,
      "<p>Qualquer dúvida, é só chamar. Boa experiência no easyshift! 🚀</p>",
    ].join("\n");

    try {
      await mailRef.set({
        to: [email],
        message: { subject, text, html },
        // Metadados próprios pra rastreio — a extensão ignora
        // campos extras, e nada cria índice nesses campos.
        meta: {
          kind: "welcome",
          allowedEmailId: event.params.emailId,
          enqueuedAt: admin.firestore.FieldValue.serverTimestamp(),
        },
      });
      logger.info(`E-mail de boas-vindas enfileirado pra ${email} (mail/${mailDocId})`);
    } catch (err) {
      logger.error(`Falha ao enfileirar e-mail de boas-vindas pra ${email}`, err);
    }
  },
);

exports.onSwapCreated = onDocumentCreated(
  // Region default us-central1 — alinha com o Firestore deste projeto.
  // Se você mover o Firestore pra outra região, atualize aqui.
  { document: "swaps/{swapId}", region: "us-central1" },
  async (event) => {
    const snap = event.data;
    if (!snap) {
      logger.warn("Swap criado sem snapshot");
      return;
    }
    const swap = snap.data();
    const swapId = event.params.swapId;

    // Defesa em profundidade — o app só cria swap em PENDING, mas
    // se um admin / migração criar com outro status, a função não
    // dispara push (não faz sentido notificar troca já aceita/recusada).
    if (swap.status && swap.status !== "PENDING") {
      logger.info(`Swap ${swapId} criado já com status ${swap.status}, pulando push`);
      return;
    }

    const targetId = swap.targetId;
    const requesterId = swap.requesterId;
    if (!targetId || !requesterId) {
      logger.warn(`Swap ${swapId} sem targetId/requesterId, pulando push`);
      return;
    }

    // Lê em paralelo: doc do target (pra pegar o token FCM) e doc do
    // requester (pra colocar o nome no corpo da notificação).
    const [targetSnap, requesterSnap] = await Promise.all([
      db.collection("users").doc(targetId).get(),
      db.collection("users").doc(requesterId).get(),
    ]);

    if (!targetSnap.exists) {
      logger.warn(`Target ${targetId} não existe em users, pulando push`);
      return;
    }

    const target = targetSnap.data();
    const token = target.fcmToken;
    if (!token) {
      // Cenário comum quando o usuário nunca abriu o app no celular,
      // ou foi instalado antes do FCM ser ligado. Sem token não dá
      // pra entregar push — só logar e seguir.
      logger.info(`Target ${targetId} sem fcmToken, pulando push`);
      return;
    }

    const requesterName = requesterSnap.exists
      ? (requesterSnap.data().name || "Um colega")
      : "Um colega";

    const title = "Nova solicitação de troca";
    const body = `${requesterName} pediu pra trocar um dia de trabalho com você.`;

    try {
      await messaging.send({
        token,
        notification: { title, body },
        // `data` é entregue em qualquer estado do app (foreground,
        // background, fechado). O app pode usar `swapId` no futuro
        // pra deep-link direto na tela de Trocas.
        data: {
          swapId,
          requesterId,
          targetId,
        },
        android: {
          priority: "high",
          notification: {
            channelId: "swap_requests",
          },
        },
      });
      logger.info(`Push enviado pro target ${targetId} (swap ${swapId})`);
    } catch (err) {
      // Token inválido (`messaging/registration-token-not-registered`)
      // significa que o usuário desinstalou o app ou o token foi
      // rotacionado e o doc tá desatualizado. Limpamos o campo pra
      // não tentar entregar de novo até o app re-sincronizar.
      const code = err && err.errorInfo && err.errorInfo.code;
      if (
        code === "messaging/registration-token-not-registered" ||
        code === "messaging/invalid-registration-token"
      ) {
        logger.warn(`Token inválido pro target ${targetId}, limpando`);
        await db.collection("users").doc(targetId).update({ fcmToken: admin.firestore.FieldValue.delete() });
        return;
      }
      logger.error(`Erro ao enviar push pro target ${targetId}`, err);
    }
  },
);
