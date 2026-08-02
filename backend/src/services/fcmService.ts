import { messaging } from "../firebaseAdmin";
import { getUserFcmTokens, removeFcmTokens } from "./usersService";

export interface PushPayload {
  title: string;
  body: string;
  data: Record<string, string>;
}

/**
 * Sends a data-only push (no `notification` key) so the client's FirebaseMessagingService always
 * gets onMessageReceived -- including while backgrounded -- and can decide for itself whether to
 * show a banner (e.g. suppress it if the user already has that exact chat open in-app). A
 * notification-key payload would instead let the OS auto-display it with no client say in the
 * matter, which breaks that suppression.
 */
export async function sendPushToUser(uid: string, payload: PushPayload): Promise<void> {
  const tokens = await getUserFcmTokens(uid);
  if (tokens.length === 0) return;

  const response = await messaging.sendEachForMulticast({
    tokens,
    data: { title: payload.title, body: payload.body, ...payload.data },
  });

  const deadTokens: string[] = [];
  response.responses.forEach((result, i) => {
    const code = result.error?.code;
    if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-argument") {
      deadTokens.push(tokens[i]);
    }
  });
  if (deadTokens.length > 0) {
    await removeFcmTokens(uid, deadTokens);
  }
}
