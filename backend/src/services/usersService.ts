import { firestore } from "../firebaseAdmin";

export interface UserProfileDoc {
  uid: string;
  email: string | null;
  displayName: string | null;
  createdAt: number;
  updatedAt: number;
  fcmTokens: string[];
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
