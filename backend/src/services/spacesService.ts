import { firestore } from "../firebaseAdmin";

export interface SpaceDoc {
  id: string;
  ownerUid: string;
  name: string;
  premise: string;
  simDate: string;
  simStatus: "running" | "paused";
  personaCount: number;
  createdAt: number;
  updatedAt: number;
  lastTickAt: number;
  /** Debounces the "user opened this Space" spontaneous-activity beat (see orchestrator/exchange.ts's maybeGenerateSpontaneousActivity) so it doesn't fire on every single visit. */
  lastSpontaneousAt?: number;
}

export class ForbiddenError extends Error {}
export class NotFoundError extends Error {}

export async function getSpace(spaceId: string): Promise<SpaceDoc | null> {
  const snap = await firestore.collection("spaces").doc(spaceId).get();
  if (!snap.exists) return null;
  return { id: snap.id, ...(snap.data() as Omit<SpaceDoc, "id">) };
}

/** Throws NotFoundError / ForbiddenError; otherwise returns the space doc. */
export async function assertOwnership(spaceId: string, uid: string): Promise<SpaceDoc> {
  const space = await getSpace(spaceId);
  if (!space) throw new NotFoundError(`Space ${spaceId} not found`);
  if (space.ownerUid !== uid) throw new ForbiddenError(`Not the owner of space ${spaceId}`);
  return space;
}

/** Deletes a space and all its subcollections. Admin SDK only -- there's no client-side equivalent. */
export async function deleteSpaceRecursive(spaceId: string): Promise<void> {
  await firestore.recursiveDelete(firestore.collection("spaces").doc(spaceId));
}

export async function getPersona(spaceId: string, personaId: string) {
  const snap = await firestore.collection("spaces").doc(spaceId).collection("personas").doc(personaId).get();
  if (!snap.exists) return null;
  return { id: snap.id, ...snap.data() };
}

export async function getAllPersonas(spaceId: string) {
  const snap = await firestore.collection("spaces").doc(spaceId).collection("personas").get();
  return snap.docs.map((doc) => ({ id: doc.id, ...doc.data() }));
}
