/**
 * Cloud Functions do Easy Folgas.
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
