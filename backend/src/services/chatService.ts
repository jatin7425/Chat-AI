import { firestore } from "../firebaseAdmin";
import { ChatMessage } from "../llm/llmClient";

export interface RelationshipEmotions {
  affection: number;
  love: number;
  lust: number;
  trust: number;
}

const EMPTY_EMOTIONS: RelationshipEmotions = { affection: 0, love: 0, lust: 0, trust: 0 };

export interface PersonaDoc {
  id: string;
  name: string;
  dob: string;
  gender: string;
  relationshipToUser: string;
  bio: string;
  background: string;
  mood: number;
  aggressiveness: number;
  appearance: Record<string, string>;
  relationshipsToOtherPersonas: Record<string, string>;
  emotionsTowardUser: RelationshipEmotions;
  emotionsTowardPersonas: Record<string, RelationshipEmotions>;
  currentPlaceId: string;
  currentPlaceName: string;
  coreMemories: string[];
  pendingTopics: string[];
  awaitingUserReply: boolean;
}

function personasCollection(spaceId: string) {
  return firestore.collection("spaces").doc(spaceId).collection("personas");
}

function chatMessagesCollection(spaceId: string, personaId: string) {
  return firestore.collection("spaces").doc(spaceId).collection("chats").doc(personaId).collection("messages");
}

function storyFeedCollection(spaceId: string) {
  return firestore.collection("spaces").doc(spaceId).collection("storyFeed");
}

function needsInputCollection(spaceId: string) {
  return firestore.collection("spaces").doc(spaceId).collection("needsInput");
}

function npcTranscriptCollection(spaceId: string, conversationId: string) {
  return firestore.collection("spaces").doc(spaceId).collection("npcTranscripts").doc(conversationId).collection("messages");
}

function groupChatsCollection(spaceId: string) {
  return firestore.collection("spaces").doc(spaceId).collection("groupChats");
}

function groupChatMessagesCollection(spaceId: string, groupChatId: string) {
  return groupChatsCollection(spaceId).doc(groupChatId).collection("messages");
}

function placesCollection(spaceId: string) {
  return firestore.collection("spaces").doc(spaceId).collection("places");
}

function activityLogCollection(spaceId: string, personaId: string) {
  return personasCollection(spaceId).doc(personaId).collection("activityLog");
}

export interface PlaceDoc {
  id: string;
  name: string;
  description: string;
}

export async function getPlaces(spaceId: string): Promise<PlaceDoc[]> {
  const snap = await placesCollection(spaceId).get();
  return snap.docs.map((doc) => ({
    id: doc.id,
    name: doc.data().name ?? "",
    description: doc.data().description ?? "",
  }));
}

export async function findPlaceByName(spaceId: string, name: string): Promise<PlaceDoc | null> {
  const normalized = name.trim().toLowerCase();
  if (!normalized) return null;
  const places = await getPlaces(spaceId);
  const match = places.find((p) => p.name.trim().toLowerCase() === normalized);
  return match ?? null;
}

export async function createPlace(spaceId: string, name: string, description: string): Promise<PlaceDoc> {
  const now = Date.now();
  const ref = placesCollection(spaceId).doc();
  await ref.set({ name, description, createdAt: now, updatedAt: now });
  return { id: ref.id, name, description };
}

/**
 * spaceId + personaId/personaName are denormalized onto every entry (not just implied by the
 * subcollection path) specifically so a Firestore collectionGroup("activityLog") query can power
 * a single space-wide feed across every persona, filtered by spaceId and ordered by createdAt.
 */
export async function appendActivityLogEntry(
  spaceId: string,
  personaId: string,
  personaName: string,
  type: "chat" | "move" | "mood_shift",
  description: string,
  place?: { id: string; name: string }
): Promise<void> {
  await activityLogCollection(spaceId, personaId).add({
    spaceId,
    personaId,
    personaName,
    type,
    description,
    placeId: place?.id ?? "",
    placeName: place?.name ?? "",
    createdAt: Date.now(),
  });
}

/**
 * A persona's own cross-conversation memory: their last N activity-log entries (already spanning
 * direct chat, group chat, and NPC<->NPC tick exchanges, since every one of those paths appends
 * here), oldest first, as plain description strings for prompt injection. This is deliberately
 * the SAME log the space-wide activity feed reads -- one source of truth, no separate memory
 * store to keep in sync.
 */
export async function getPersonaMemory(spaceId: string, personaId: string, limit = 20): Promise<string[]> {
  const snap = await activityLogCollection(spaceId, personaId).orderBy("createdAt", "desc").limit(limit).get();
  return snap.docs.map((doc) => doc.data().description as string).reverse();
}

const MAX_PENDING_TOPICS = 20;

/**
 * Proactive-message queue: a persona should only ping the user with ONE unprompted direct
 * message while the user's away, even if several things happened that they'd want to bring up --
 * anything beyond the first queues here instead of spamming multiple messages, and gets raised
 * one at a time as the user keeps replying (see popPendingTopic, called from the send-message
 * route on every user turn).
 */
export async function enqueuePendingTopic(spaceId: string, personaId: string, topic: string): Promise<void> {
  const ref = personasCollection(spaceId).doc(personaId);
  const snap = await ref.get();
  if (!snap.exists) return;
  const existing: string[] = snap.data()?.pendingTopics ?? [];
  const updated = [...existing, topic].slice(-MAX_PENDING_TOPICS);
  await ref.update({ pendingTopics: updated, updatedAt: Date.now() });
}

/** Pops the oldest queued topic (FIFO) so it can be woven into the persona's next reply to the user. */
export async function popPendingTopic(spaceId: string, personaId: string): Promise<string | null> {
  const ref = personasCollection(spaceId).doc(personaId);
  const snap = await ref.get();
  if (!snap.exists) return null;
  const existing: string[] = snap.data()?.pendingTopics ?? [];
  if (existing.length === 0) return null;
  const [next, ...rest] = existing;
  await ref.update({ pendingTopics: rest, updatedAt: Date.now() });
  return next;
}

export async function setAwaitingUserReply(spaceId: string, personaId: string, value: boolean): Promise<void> {
  await personasCollection(spaceId).doc(personaId).update({ awaitingUserReply: value, updatedAt: Date.now() });
}

/**
 * Delivers a proactive update to the user's direct chat with a persona, respecting the queue: if
 * the persona already has an unanswered proactive message out, this queues the topic instead of
 * sending another message on top of it. Used by the orchestrator tick whenever a completed task
 * (a delegate handoff reporting back, a self-followup, spontaneous activity worth mentioning)
 * wants to reach the user.
 */
/** Returns true if the message was actually delivered to the chat, false if it was queued instead -- callers should only push a push-notification/UI ping when this is true. */
export async function deliverOrQueueToUser(
  spaceId: string,
  personaId: string,
  messageText: string,
  queueTopicIfBusy: string
): Promise<boolean> {
  const persona = await getPersonaDoc(spaceId, personaId);
  if (!persona) return false;
  if (persona.awaitingUserReply) {
    await enqueuePendingTopic(spaceId, personaId, queueTopicIfBusy);
    return false;
  }
  await appendDirectMessage(spaceId, personaId, "assistant", messageText);
  await setAwaitingUserReply(spaceId, personaId, true);
  return true;
}

/** Moves a persona to a place, updating the denormalized name on the persona doc and logging the move. */
export async function movePersonaToPlace(
  spaceId: string,
  personaId: string,
  personaName: string,
  place: PlaceDoc
): Promise<void> {
  await personasCollection(spaceId).doc(personaId).update({
    currentPlaceId: place.id,
    currentPlaceName: place.name,
    updatedAt: Date.now(),
  });
  await appendActivityLogEntry(spaceId, personaId, personaName, "move", `${personaName} moved to ${place.name}`, place);
}

export interface GroupChatDoc {
  id: string;
  name: string;
  personaIds: string[];
}

export interface GroupChatMessage {
  role: "user" | "assistant";
  speakerPersonaId: string;
  text: string;
}

export async function getGroupChat(spaceId: string, groupChatId: string): Promise<GroupChatDoc | null> {
  const snap = await groupChatsCollection(spaceId).doc(groupChatId).get();
  if (!snap.exists) return null;
  const data = snap.data()!;
  return {
    id: snap.id,
    name: data.name ?? "",
    personaIds: data.personaIds ?? [],
  };
}

export async function getRecentGroupChatMessages(
  spaceId: string,
  groupChatId: string,
  limit = 20
): Promise<GroupChatMessage[]> {
  const snap = await groupChatMessagesCollection(spaceId, groupChatId).orderBy("createdAt", "desc").limit(limit).get();
  return snap.docs
    .map((doc) => doc.data())
    .reverse()
    .map((data) => ({
      role: data.role as "user" | "assistant",
      speakerPersonaId: data.speakerPersonaId ?? "",
      text: data.text as string,
    }));
}

export async function appendGroupChatMessage(
  spaceId: string,
  groupChatId: string,
  role: "user" | "assistant",
  text: string,
  speakerPersonaId = ""
): Promise<void> {
  await groupChatMessagesCollection(spaceId, groupChatId).add({ role, speakerPersonaId, text, createdAt: Date.now() });
}

/**
 * Flattens a group chat's mixed-speaker history into plain user/assistant turns for a specific
 * persona's point of view -- their own past lines become "assistant", everyone else's (the real
 * user AND other personas) become "user", with other personas' lines prefixed by name so the
 * model can tell voices apart (chat-completions roles don't support more than one speaker).
 */
export function toPersonaChatHistory(
  messages: GroupChatMessage[],
  personaId: string,
  nameById: Record<string, string>
): ChatMessage[] {
  return messages.map((m) => {
    if (m.role === "assistant" && m.speakerPersonaId === personaId) {
      return { role: "assistant", content: m.text };
    }
    if (m.role === "assistant") {
      const name = nameById[m.speakerPersonaId] ?? "Someone";
      return { role: "user", content: `[${name}]: ${m.text}` };
    }
    return { role: "user", content: `[User]: ${m.text}` };
  });
}

function toPersonaDoc(id: string, data: FirebaseFirestore.DocumentData): PersonaDoc {
  return {
    id,
    name: data.name ?? "",
    dob: data.dob ?? "",
    gender: data.gender ?? "",
    relationshipToUser: data.relationshipToUser ?? "",
    bio: data.bio ?? "",
    background: data.background ?? "",
    mood: data.mood ?? 0,
    aggressiveness: data.aggressiveness ?? 0,
    appearance: data.appearance ?? {},
    relationshipsToOtherPersonas: data.relationshipsToOtherPersonas ?? {},
    emotionsTowardUser: { ...EMPTY_EMOTIONS, ...(data.emotionsTowardUser ?? {}) },
    emotionsTowardPersonas: data.emotionsTowardPersonas ?? {},
    currentPlaceId: data.currentPlaceId ?? "",
    currentPlaceName: data.currentPlaceName ?? "",
    coreMemories: data.coreMemories ?? [],
    pendingTopics: data.pendingTopics ?? [],
    awaitingUserReply: data.awaitingUserReply ?? false,
  };
}

export async function getPersonaDoc(spaceId: string, personaId: string): Promise<PersonaDoc | null> {
  const snap = await personasCollection(spaceId).doc(personaId).get();
  if (!snap.exists) return null;
  return toPersonaDoc(snap.id, snap.data()!);
}

export async function getAllPersonaDocs(spaceId: string): Promise<PersonaDoc[]> {
  const snap = await personasCollection(spaceId).get();
  return snap.docs.map((doc) => toPersonaDoc(doc.id, doc.data()));
}

const MAX_CORE_MEMORIES = 30;

/**
 * Durable long-term memory, distinct from the rolling activity-log window: only written at
 * genuinely significant moments (a commitment made, a relationship formed, a place discovered)
 * rather than every single exchange, so it doesn't scroll out the way a fixed-size recent-log
 * window would -- closer to how a person remembers the things that mattered and lets the small
 * talk fade. Capped and FIFO-trimmed so the prompt doesn't grow unbounded over a long-running sim.
 */
export async function appendCoreMemory(spaceId: string, personaId: string, note: string): Promise<void> {
  const ref = personasCollection(spaceId).doc(personaId);
  const snap = await ref.get();
  if (!snap.exists) return;
  const existing: string[] = snap.data()?.coreMemories ?? [];
  const updated = [...existing, note].slice(-MAX_CORE_MEMORIES);
  await ref.update({ coreMemories: updated, updatedAt: Date.now() });
}

/**
 * Matches on exact full name first, then first-name / substring matching, then finally against
 * relationshipToUser (e.g. "Co-founder & CTO") -- delegation detection often extracts a title or
 * role instead of an actual name ("check with CTO" instead of "check with Sanya"), and without
 * this fallback that resolves to nobody, silently breaking the persona-to-persona handoff and
 * spawning a spurious "needs your input" card for someone who already exists.
 */
export async function findPersonaByName(spaceId: string, name: string): Promise<PersonaDoc | null> {
  const snap = await personasCollection(spaceId).get();
  const normalized = name.trim().toLowerCase();
  if (!normalized) return null;

  const exact = snap.docs.find((doc) => (doc.data().name ?? "").trim().toLowerCase() === normalized);
  if (exact) return getPersonaDoc(spaceId, exact.id);

  const partial = snap.docs.find((doc) => {
    const fullName = (doc.data().name ?? "").trim().toLowerCase();
    if (!fullName) return false;
    const firstName = fullName.split(/\s+/)[0];
    return firstName === normalized || fullName.includes(normalized) || normalized.includes(fullName);
  });
  if (partial) return getPersonaDoc(spaceId, partial.id);

  // Word-level overlap rather than strict substring containment -- the extracted name often
  // includes an article ("the CTO"), which isn't a contiguous substring of "Co-founder & CTO"
  // even though they clearly refer to the same role. Stripping stopwords and comparing
  // significant words catches this without needing exact phrasing to line up.
  const STOPWORDS = new Set(["the", "a", "an", "our", "my", "your", "of", "to"]);
  const significantWords = (s: string) => s.split(/[^a-z0-9]+/).filter((w) => w.length > 1 && !STOPWORDS.has(w));
  const targetWords = significantWords(normalized);

  const byRole = snap.docs.find((doc) => {
    const relationship = (doc.data().relationshipToUser ?? "").trim().toLowerCase();
    if (!relationship) return false;
    const relationshipWords = new Set(significantWords(relationship));
    return targetWords.some((w) => relationshipWords.has(w));
  });
  if (!byRole) return null;
  return getPersonaDoc(spaceId, byRole.id);
}

export interface DelegationTargetResolution {
  target: PersonaDoc | null;
  /** True when the extracted name resolved back to the SPEAKER themselves (e.g. Sanya IS the
   *  CTO, so her own reply mentioning "the CTO" matches her). Callers should treat this as a
   *  self-followup, not a delegate handoff to someone -- "reach out to yourself" isn't a real
   *  delegation, and creating one produced literal "X is planning to reach out to X" tasks. */
  isSelfReference: boolean;
}

export async function resolveDelegationTarget(
  spaceId: string,
  targetName: string,
  speakerPersonaId: string
): Promise<DelegationTargetResolution> {
  const match = await findPersonaByName(spaceId, targetName);
  if (match && match.id === speakerPersonaId) {
    return { target: null, isSelfReference: true };
  }
  return { target: match, isSelfReference: false };
}

export async function getRecentDirectMessages(spaceId: string, personaId: string, limit = 12): Promise<ChatMessage[]> {
  const snap = await chatMessagesCollection(spaceId, personaId).orderBy("createdAt", "desc").limit(limit).get();
  return snap.docs
    .map((doc) => doc.data())
    .reverse()
    .map((data) => ({ role: data.role as "user" | "assistant", content: data.text as string }));
}

export async function appendDirectMessage(
  spaceId: string,
  personaId: string,
  role: "user" | "assistant",
  text: string
): Promise<void> {
  await chatMessagesCollection(spaceId, personaId).add({ role, text, createdAt: Date.now() });
}

export async function appendNpcTranscriptMessage(
  spaceId: string,
  conversationId: string,
  personaId: string,
  text: string
): Promise<void> {
  await npcTranscriptCollection(spaceId, conversationId).add({ personaId, text, createdAt: Date.now() });
}

export interface NpcTranscriptLine {
  personaId: string;
  text: string;
}

/** Chronological (oldest first) -- lets a bounded exchange feed its own prior lines back to the model each step so later turns respond coherently instead of restarting the conversation from nothing. */
export async function getNpcTranscriptMessages(spaceId: string, conversationId: string): Promise<NpcTranscriptLine[]> {
  const snap = await npcTranscriptCollection(spaceId, conversationId).orderBy("createdAt", "asc").get();
  return snap.docs.map((doc) => doc.data() as NpcTranscriptLine);
}

export interface CreateStoryFeedTaskInput {
  description: string;
  speakerPersonaId: string;
  targetPersonaName: string;
  targetPersonaId?: string | null;
  relatedChatPersonaId: string;
  topic: string;
  /** "delegate" (default) simulates speaker reaching out to targetPersonaId; "self_followup" has
   *  the speaker themselves report back to the user once the tick loop gets to it, for
   *  commitments with no named other party ("I'll get back to you"). */
  kind?: "delegate" | "self_followup";
  /** Higher runs first within a tick. Explicit user-assigned/backscanned commitments default
   *  higher (10) than ones the orchestrator merely inferred from a reply (5). */
  priority?: number;
}

/** Returns the new task's id -- the event-driven orchestrator kicks off (or resumes) a task's exchange chain immediately after creating it, so callers need the id right away rather than discovering it on a later poll. */
export async function createStoryFeedTask(spaceId: string, input: CreateStoryFeedTaskInput): Promise<string> {
  const now = Date.now();
  const ref = storyFeedCollection(spaceId).doc();
  await ref.set({
    description: input.description,
    status: "pending",
    speakerPersonaId: input.speakerPersonaId,
    targetPersonaId: input.targetPersonaId ?? null,
    targetPersonaName: input.targetPersonaName,
    relatedChatPersonaId: input.relatedChatPersonaId,
    topic: input.topic,
    kind: input.kind ?? "delegate",
    priority: input.priority ?? 5,
    exchangeCount: 0,
    blockedReason: null,
    createdAt: now,
    updatedAt: now,
  });
  return ref.id;
}

export interface StoryFeedTaskDoc {
  id: string;
  description: string;
  status: "pending" | "in_progress" | "blocked" | "done";
  speakerPersonaId: string;
  targetPersonaId: string | null;
  targetPersonaName: string;
  relatedChatPersonaId: string;
  topic: string;
  kind?: "delegate" | "self_followup";
  priority?: number;
  exchangeCount?: number;
}

export async function getStoryFeedTask(spaceId: string, taskId: string): Promise<StoryFeedTaskDoc | null> {
  const doc = await storyFeedCollection(spaceId).doc(taskId).get();
  if (!doc.exists) return null;
  return { id: doc.id, ...(doc.data() as Omit<StoryFeedTaskDoc, "id">) };
}

/** Checks whether an equivalent pending/blocked/in_progress task already exists, so re-detecting the same commitment across retries or a startup backscan doesn't spawn duplicates. */
export async function hasSimilarPendingTask(spaceId: string, speakerPersonaId: string, topic: string): Promise<boolean> {
  const snap = await storyFeedCollection(spaceId)
    .where("speakerPersonaId", "==", speakerPersonaId)
    .where("status", "in", ["pending", "blocked", "in_progress"])
    .get();
  const normalized = topic.trim().toLowerCase();
  if (!normalized) return false;
  return snap.docs.some((doc) => {
    const existingTopic = (doc.data().topic ?? "").trim().toLowerCase();
    return existingTopic && (existingTopic.includes(normalized) || normalized.includes(existingTopic));
  });
}

export async function createNeedsInput(
  spaceId: string,
  question: string,
  targetPersonaName: string,
  targetChatPersonaId: string
): Promise<void> {
  await needsInputCollection(spaceId).add({
    question,
    targetPersonaName,
    targetChatPersonaId,
    status: "pending",
    createdAt: Date.now(),
  });
}

/** Marks any pending needsInput docs for this target name as resolved once the persona exists. */
export async function resolveNeedsInputForName(spaceId: string, targetPersonaName: string): Promise<void> {
  const normalized = targetPersonaName.trim().toLowerCase();
  const snap = await needsInputCollection(spaceId).where("status", "==", "pending").get();
  const batch = firestore.batch();
  snap.docs.forEach((doc) => {
    if ((doc.data().targetPersonaName ?? "").trim().toLowerCase() === normalized) {
      batch.update(doc.ref, { status: "resolved" });
    }
  });
  await batch.commit();
}

/**
 * User-driven dismissal for when the extracted "target" was never meant to be a persona at all
 * (a role reference like "the executive" that just doesn't map to anyone real) -- marks the
 * needsInput card dismissed and closes out the matching blocked storyFeed task so the tick loop
 * stops retrying a lookup that will never resolve.
 */
export async function dismissNeedsInput(spaceId: string, requestId: string): Promise<void> {
  const docRef = needsInputCollection(spaceId).doc(requestId);
  const doc = await docRef.get();
  if (!doc.exists) return;

  const targetPersonaName = ((doc.data()?.targetPersonaName as string) ?? "").trim().toLowerCase();

  const batch = firestore.batch();
  batch.update(docRef, { status: "dismissed" });

  if (targetPersonaName) {
    const tasksSnap = await storyFeedCollection(spaceId)
      .where("status", "in", ["pending", "blocked"])
      .get();
    tasksSnap.docs.forEach((taskDoc) => {
      if ((taskDoc.data().targetPersonaName ?? "").trim().toLowerCase() === targetPersonaName) {
        batch.update(taskDoc.ref, {
          status: "done",
          blockedReason: "Dismissed by user -- not a real persona",
          updatedAt: Date.now(),
        });
      }
    });
  }

  await batch.commit();
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}

/**
 * Nudges a persona's mood/aggressiveness and (optionally) their targeted feelings toward a
 * specific other persona or the user, based on a coarse sentiment read of an exchange. Small,
 * bounded deltas -- this runs every tick a conversation happens, so it should feel gradual, not
 * swingy.
 */
export function driftEmotions(
  current: { affection: number; love: number; lust: number; trust: number },
  sentiment: "positive" | "neutral" | "negative"
): RelationshipEmotions {
  const delta = sentiment === "positive" ? 3 : sentiment === "negative" ? -3 : 0;
  return {
    affection: clamp(current.affection + delta, 0, 100),
    love: clamp(current.love + Math.round(delta * 0.5), 0, 100),
    lust: current.lust, // never auto-drifted; would require far more deliberate signal than a sentiment heuristic
    trust: clamp(current.trust + (sentiment === "negative" ? delta * 2 : delta), 0, 100),
  };
}

export function driftMood(currentMood: number, sentiment: "positive" | "neutral" | "negative"): number {
  const delta = sentiment === "positive" ? 4 : sentiment === "negative" ? -4 : 0;
  // Also drift a little toward baseline (0) each tick so one bad exchange doesn't linger forever.
  const towardBaseline = currentMood > 0 ? -1 : currentMood < 0 ? 1 : 0;
  return clamp(currentMood + delta + towardBaseline, -100, 100);
}

/** Very coarse keyword-based sentiment read -- a placeholder for an LLM-based classifier later. */
export function coarseSentiment(text: string): "positive" | "neutral" | "negative" {
  const lower = text.toLowerCase();
  const positiveWords = ["thank", "great", "glad", "love", "happy", "appreciate", "good", "yes", "agree", "excited"];
  const negativeWords = ["angry", "upset", "no", "refuse", "hate", "annoyed", "frustrat", "disappoint", "worried", "concern"];
  const positiveHits = positiveWords.filter((w) => lower.includes(w)).length;
  const negativeHits = negativeWords.filter((w) => lower.includes(w)).length;
  if (positiveHits > negativeHits) return "positive";
  if (negativeHits > positiveHits) return "negative";
  return "neutral";
}

export async function updatePersonaEmotions(
  spaceId: string,
  personaId: string,
  updates: { mood?: number; aggressiveness?: number; emotionsTowardUser?: RelationshipEmotions }
): Promise<void> {
  await personasCollection(spaceId)
    .doc(personaId)
    .update({ ...updates, updatedAt: Date.now() });
}

export async function updatePersonaEmotionsTowardPersona(
  spaceId: string,
  personaId: string,
  targetPersonaId: string,
  emotions: RelationshipEmotions
): Promise<void> {
  await personasCollection(spaceId)
    .doc(personaId)
    .update({ [`emotionsTowardPersonas.${targetPersonaId}`]: emotions, updatedAt: Date.now() });
}
