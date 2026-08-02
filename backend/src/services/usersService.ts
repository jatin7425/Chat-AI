import { firestore, FieldValue } from "../firebaseAdmin";

export interface UserProfileDoc {
  uid: string;
  email: string | null;
  displayName: string | null;
  createdAt: number;
  updatedAt: number;
  fcmTokens: string[];
  llmBaseUrl?: string;
  llmModel?: string;
}

/**
 * Upserts users/{uid} -- called on every successful sign-in/sign-up (both SSO and
 * email/password) so the backend has a durable record of the user independent of Firebase
 * Auth itself, and a place to hang future per-user data (fcmTokens, etc.) off of.
 */
export async function upsertUserProfile(
  uid: string,
  email: string | null,
  displayName: string | null
): Promise<UserProfileDoc> {
  const ref = firestore.collection("users").doc(uid);
  const snap = await ref.get();
  const now = Date.now();

  if (!snap.exists) {
    const doc: UserProfileDoc = {
      uid,
      email,
      displayName,
      createdAt: now,
      updatedAt: now,
      fcmTokens: [],
    };
    await ref.set(doc);
    return doc;
  }

  const existing = snap.data() as UserProfileDoc;
  const updates: Partial<UserProfileDoc> = { updatedAt: now };
  if (email) updates.email = email;
  if (displayName) updates.displayName = displayName;
  await ref.set(updates, { merge: true });

  return { ...existing, ...updates };
}

/** Adds an FCM registration token for push delivery, deduped via arrayUnion since a device's token can be re-registered on every app open. */
export async function addFcmToken(uid: string, token: string): Promise<void> {
  await firestore
    .collection("users")
    .doc(uid)
    .set({ fcmTokens: FieldValue.arrayUnion(token) }, { merge: true });
}

/** Prunes tokens FCM reported as dead/unregistered so future sends don't keep retrying them. */
export async function removeFcmTokens(uid: string, tokens: string[]): Promise<void> {
  if (tokens.length === 0) return;
  await firestore
    .collection("users")
    .doc(uid)
    .set({ fcmTokens: FieldValue.arrayRemove(...tokens) }, { merge: true });
}

export async function getUserFcmTokens(uid: string): Promise<string[]> {
  const snap = await firestore.collection("users").doc(uid).get();
  const data = snap.data() as UserProfileDoc | undefined;
  return data?.fcmTokens ?? [];
}

/**
 * The user's own LiteLLM connection (base URL + model), set from the mobile app's Settings ->
 * LiteLLM Server / Default Model screens. The backend -- not the mobile client -- calls this
 * server to generate persona replies, so every LLM call resolves it per-request from here
 * rather than from a single global .env value.
 */
export async function getUserLlmConfig(uid: string): Promise<{ baseUrl: string; model: string }> {
  const snap = await firestore.collection("users").doc(uid).get();
  const data = snap.data() as UserProfileDoc | undefined;
  const baseUrl = data?.llmBaseUrl?.trim() ?? "";
  const model = data?.llmModel?.trim() ?? "";
  if (!baseUrl || !model) {
    throw new Error(
      "No LiteLLM server/model configured yet -- set it in the app under Settings > LiteLLM Server and Default Model."
    );
  }
  return { baseUrl, model };
}
