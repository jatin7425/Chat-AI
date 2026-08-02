import { firestore } from "../firebaseAdmin";
import { chatComplete } from "../llm/llmClient";
import { getUserLlmConfig } from "../services/usersService";
import { detectCommitment } from "./commitmentDetector";
import { detectNewPlaceMention } from "./placeDetector";
import { sendPushToUser } from "../services/fcmService";
import {
  appendActivityLogEntry,
  appendCoreMemory,
  appendNpcTranscriptMessage,
  coarseSentiment,
  createNeedsInput,
  createPlace,
  createStoryFeedTask,
  deliverOrQueueToUser,
  driftEmotions,
  driftMood,
  findPersonaByName,
  findPlaceByName,
  getAllPersonaDocs,
  getPersonaDoc,
  getPersonaMemory,
  getPlaces,
  hasSimilarPendingTask,
  movePersonaToPlace,
  resolveDelegationTarget,
  resolveNeedsInputForName,
  updatePersonaEmotions,
  updatePersonaEmotionsTowardPersona,
  RelationshipEmotions,
  PersonaDoc,
} from "../services/chatService";
import { buildNpcTurnSystemPrompt, buildPersonaSystemPrompt, PersonaPromptContext } from "./promptBuilder";

const EMPTY_EMOTIONS: RelationshipEmotions = { affection: 0, love: 0, lust: 0, trust: 0 };

interface SpaceDoc {
  id: string;
  ownerUid: string;
  name: string;
  premise: string;
  simDate: string;
}

interface StoryFeedTaskDoc {
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
}

async function toPromptContext(
  persona: PersonaDoc,
  space: SpaceDoc,
  emotionsTowardUser?: RelationshipEmotions
): Promise<PersonaPromptContext> {
  return {
    name: persona.name,
    background: persona.background,
    bio: persona.bio,
    appearance: persona.appearance,
    mood: persona.mood,
    aggressiveness: persona.aggressiveness,
    relationshipToUser: persona.relationshipToUser,
    emotionsTowardUser,
    spacePremise: space.premise,
    spaceSimDate: space.simDate,
    coreMemories: persona.coreMemories,
    recentMemory: await getPersonaMemory(space.id, persona.id, 20),
  };
}

/** Best-effort: detects a commitment in one NPC line and spawns a cascading StoryFeed task -- lets promises made WITHIN the simulation (not just direct chat) also become real. */
async function followUpOnCommitment(
  space: SpaceDoc,
  speakerPersona: PersonaDoc,
  line: string,
  llmConfig: { baseUrl: string; model: string }
): Promise<void> {
  try {
    const result = await detectCommitment(line, llmConfig);
    if (result.isMilestone && result.milestoneNote) {
      await appendCoreMemory(space.id, speakerPersona.id, `${result.milestoneNote} (${space.simDate || "undated"})`);
    }
    if (!result.commits) return;
    if (await hasSimilarPendingTask(space.id, speakerPersona.id, result.topic)) return;

    const { target, isSelfReference } = result.targetName
      ? await resolveDelegationTarget(space.id, result.targetName, speakerPersona.id)
      : { target: null, isSelfReference: false };

    if (target) {
      await createStoryFeedTask(space.id, {
        description: `${speakerPersona.name} is planning to reach out to ${target.name}`,
        speakerPersonaId: speakerPersona.id,
        targetPersonaName: target.name,
        targetPersonaId: target.id,
        relatedChatPersonaId: speakerPersona.id,
        topic: result.topic,
        kind: "delegate",
      });
    } else if (result.targetName && !isSelfReference) {
      await createStoryFeedTask(space.id, {
        description: `${speakerPersona.name} needs to reach ${result.targetName}, who doesn't exist yet`,
        speakerPersonaId: speakerPersona.id,
        targetPersonaName: result.targetName,
        targetPersonaId: null,
        relatedChatPersonaId: speakerPersona.id,
        topic: result.topic,
        kind: "delegate",
      });
      await createNeedsInput(
        space.id,
        `${speakerPersona.name} needs to know who ${result.targetName} is to reach out to them. Create this persona?`,
        result.targetName,
        speakerPersona.id
      );
    } else {
      await createStoryFeedTask(space.id, {
        description: `${speakerPersona.name} needs to follow up with you about: ${result.topic}`,
        speakerPersonaId: speakerPersona.id,
        targetPersonaName: "",
        targetPersonaId: null,
        relatedChatPersonaId: speakerPersona.id,
        topic: result.topic,
        kind: "self_followup",
      });
    }
  } catch (err) {
    console.error(`[tick] commitment follow-up failed for ${speakerPersona.name}:`, err);
  }
}

/** Best-effort: auto-creates a Place if a line mentions somewhere new. */
async function followUpOnPlaceMention(
  space: SpaceDoc,
  line: string,
  llmConfig: { baseUrl: string; model: string }
): Promise<void> {
  try {
    const existingNames = (await getPlaces(space.id)).map((p) => p.name);
    const result = await detectNewPlaceMention(line, existingNames, llmConfig);
    if (!result.mentionsNewPlace) return;
    if (await findPlaceByName(space.id, result.name)) return;
    await createPlace(space.id, result.name, result.description);
  } catch (err) {
    console.error(`[tick] place detection failed for space ${space.id}:`, err);
  }
}

/**
 * Advances every due Story Feed task for a Space in one tick (not just one) -- this is a single
 * local-laptop deployment with no hosting cost pressure, so there's no reason to artificially
 * throttle to one task per cycle the way a multi-tenant hosted service might. Tasks are ordered
 * by priority (explicit user-assigned/backscanned commitments run before ones the orchestrator
 * merely inferred), then by age. If nothing's due, the Space still gets a spontaneous beat so it
 * doesn't just sit frozen between real events.
 */
async function processSpace(space: SpaceDoc): Promise<void> {
  let llmConfig: { baseUrl: string; model: string };
  try {
    llmConfig = await getUserLlmConfig(space.ownerUid);
  } catch {
    // No LLM configured for this user yet -- nothing to do until they set one in Settings.
    return;
  }

  const storyFeedRef = firestore.collection("spaces").doc(space.id).collection("storyFeed");
  const dueSnap = await storyFeedRef.where("status", "in", ["pending", "blocked"]).orderBy("createdAt", "asc").limit(20).get();

  if (dueSnap.empty) {
    await generateSpontaneousActivity(space, llmConfig);
  } else {
    const tasks = dueSnap.docs
      .map((doc) => ({ id: doc.id, ...doc.data() } as StoryFeedTaskDoc))
      .sort((a, b) => (b.priority ?? 5) - (a.priority ?? 5));

    for (const task of tasks) {
      try {
        await tryAdvanceTask(space, task, llmConfig);
      } catch (err) {
        console.error(`[tick] task ${task.id} in space ${space.id} failed:`, err);
      }
    }
  }

  await firestore.collection("spaces").doc(space.id).update({ lastTickAt: Date.now() });
}

async function tryAdvanceTask(
  space: SpaceDoc,
  task: StoryFeedTaskDoc,
  llmConfig: { baseUrl: string; model: string }
): Promise<void> {
  const storyFeedDocRef = firestore.collection("spaces").doc(space.id).collection("storyFeed").doc(task.id);

  if (task.kind === "self_followup") {
    const speaker = await getPersonaDoc(space.id, task.speakerPersonaId);
    if (!speaker) {
      await storyFeedDocRef.update({ status: "blocked", blockedReason: "The persona was deleted", updatedAt: Date.now() });
      return;
    }
    await storyFeedDocRef.update({ status: "in_progress", blockedReason: null, updatedAt: Date.now() });

    const speakerCtx = await toPromptContext(speaker, space, speaker.emotionsTowardUser);
    const followUpText = await chatComplete(
      [
        buildPersonaSystemPrompt(speakerCtx),
        {
          role: "user",
          content: `(Narrator note: some time has passed. Follow up with the user now, in character, in 1-2 sentences, about: ${task.topic})`,
        },
      ],
      { baseUrl: llmConfig.baseUrl, model: llmConfig.model }
    );

    const delivered = await deliverOrQueueToUser(space.id, speaker.id, followUpText, task.topic);
    await appendActivityLogEntry(space.id, speaker.id, speaker.name, "chat", `Followed up with you about ${task.topic}`);
    if (delivered) await notifyUser(space, speaker.name, followUpText, speaker.id);
    await storyFeedDocRef.update({ status: "done", updatedAt: Date.now() });
    return;
  }

  // "delegate" (default): simulate speaker reaching out to a specific other persona.
  let targetPersonaId = task.targetPersonaId;
  if (!targetPersonaId) {
    const resolved = await findPersonaByName(space.id, task.targetPersonaName);
    if (!resolved) {
      // Still doesn't exist -- leave it blocked (the needsInput doc was already created when
      // this task was first raised) and move on to the next due task instead.
      if (task.status !== "blocked") {
        await storyFeedDocRef.update({
          status: "blocked",
          blockedReason: `Persona '${task.targetPersonaName}' doesn't exist yet`,
          updatedAt: Date.now(),
        });
      }
      return;
    }
    if (resolved.id === task.speakerPersonaId) {
      // The name resolved back to the speaker themselves (e.g. a role reference like "the CTO"
      // that happens to be the speaker's own title) -- there's no one else to talk to, so this
      // isn't a real delegate exchange. Close it out rather than simulating a persona conversing
      // with themselves.
      await storyFeedDocRef.update({
        status: "done",
        blockedReason: "Target resolved to the speaker themselves -- nothing to delegate",
        updatedAt: Date.now(),
      });
      return;
    }
    targetPersonaId = resolved.id;
    await resolveNeedsInputForName(space.id, task.targetPersonaName);
  }

  const speaker = await getPersonaDoc(space.id, task.speakerPersonaId);
  const target = await getPersonaDoc(space.id, targetPersonaId);
  if (!speaker || !target) {
    await storyFeedDocRef.update({ status: "blocked", blockedReason: "A participant persona was deleted", updatedAt: Date.now() });
    return;
  }

  await storyFeedDocRef.update({ status: "in_progress", targetPersonaId, blockedReason: null, updatedAt: Date.now() });

  // Simulate a short NPC<->NPC exchange (speaker opens, target responds) -- deliberately just
  // two lines to bound LLM calls per resolved task.
  const speakerCtx = await toPromptContext(speaker, space, speaker.emotionsTowardPersonas[target.id] ?? EMPTY_EMOTIONS);
  const targetCtx = await toPromptContext(target, space, target.emotionsTowardPersonas[speaker.id] ?? EMPTY_EMOTIONS);

  const speakerLine = await chatComplete(
    [buildNpcTurnSystemPrompt({ speaker: speakerCtx, target: targetCtx, topic: task.topic }, true)],
    { baseUrl: llmConfig.baseUrl, model: llmConfig.model }
  );
  const targetLine = await chatComplete(
    [
      buildNpcTurnSystemPrompt({ speaker: speakerCtx, target: targetCtx, topic: task.topic }, false),
      { role: "user", content: speakerLine },
    ],
    { baseUrl: llmConfig.baseUrl, model: llmConfig.model }
  );

  await appendNpcTranscriptMessage(space.id, task.id, speaker.id, speakerLine);
  await appendNpcTranscriptMessage(space.id, task.id, target.id, targetLine);

  await appendActivityLogEntry(
    space.id,
    speaker.id,
    speaker.name,
    "chat",
    `Talked to ${target.name} about ${task.topic || "something"}`,
    speaker.currentPlaceId ? { id: speaker.currentPlaceId, name: speaker.currentPlaceName } : undefined
  );
  await appendActivityLogEntry(
    space.id,
    target.id,
    target.name,
    "chat",
    `Talked to ${speaker.name} about ${task.topic || "something"}`,
    target.currentPlaceId ? { id: target.currentPlaceId, name: target.currentPlaceName } : undefined
  );

  // Claims made INSIDE the simulation become reality too, and either line might mention a new place.
  await followUpOnCommitment(space, speaker, speakerLine, llmConfig);
  await followUpOnCommitment(space, target, targetLine, llmConfig);
  await followUpOnPlaceMention(space, `${speakerLine} ${targetLine}`, llmConfig);

  // Occasionally send one of the two personas somewhere else in the Space -- a light-touch way
  // for the world to feel lived-in without the LLM needing to reason about geography at all.
  try {
    const places = await getPlaces(space.id);
    if (places.length > 0 && Math.random() < 0.25) {
      const mover = Math.random() < 0.5 ? speaker : target;
      const candidates = places.filter((p) => p.id !== mover.currentPlaceId);
      const destination = candidates.length > 0 ? candidates[Math.floor(Math.random() * candidates.length)] : null;
      if (destination) {
        await movePersonaToPlace(space.id, mover.id, mover.name, destination);
      }
    }
  } catch (err) {
    console.error(`[tick] movement for space ${space.id} failed:`, err);
  }

  // Drift both personas' mood and their targeted feelings toward each other based on a coarse
  // sentiment read of the exchange -- small, gradual, never user-visible as raw transcript.
  const sentiment = coarseSentiment(`${speakerLine} ${targetLine}`);
  const newSpeakerMood = driftMood(speaker.mood, sentiment);
  const newTargetMood = driftMood(target.mood, sentiment);
  const speakerTowardTarget = driftEmotions(speaker.emotionsTowardPersonas[target.id] ?? EMPTY_EMOTIONS, sentiment);
  const targetTowardSpeaker = driftEmotions(target.emotionsTowardPersonas[speaker.id] ?? EMPTY_EMOTIONS, sentiment);

  await updatePersonaEmotions(space.id, speaker.id, { mood: newSpeakerMood });
  await updatePersonaEmotions(space.id, target.id, { mood: newTargetMood });
  await updatePersonaEmotionsTowardPersona(space.id, speaker.id, target.id, speakerTowardTarget);
  await updatePersonaEmotionsTowardPersona(space.id, target.id, speaker.id, targetTowardSpeaker);

  // Report back to the user, in the speaker's own voice, in their regular direct chat -- unless
  // the speaker already has an unanswered proactive message out, in which case this just queues.
  const reportBackPrompt = buildPersonaSystemPrompt({
    ...speakerCtx,
    emotionsTowardUser: speaker.emotionsTowardUser,
  });
  const reportBackText = await chatComplete(
    [
      reportBackPrompt,
      {
        role: "user",
        content: `(Narrator note: report back to me in 1-2 sentences, in character, that you talked to ${target.name} about: ${task.topic}. Their reaction: "${targetLine}")`,
      },
    ],
    { baseUrl: llmConfig.baseUrl, model: llmConfig.model }
  );

  const delivered = await deliverOrQueueToUser(space.id, task.relatedChatPersonaId, reportBackText, `talked to ${target.name} about ${task.topic}`);
  if (delivered) await notifyUser(space, speaker.name, reportBackText, task.relatedChatPersonaId);

  await storyFeedDocRef.update({ status: "done", updatedAt: Date.now() });
}

async function notifyUser(space: SpaceDoc, title: string, body: string, personaId: string): Promise<void> {
  await firestore.collection("notifications").doc(space.ownerUid).collection("items").add({
    spaceId: space.id,
    personaId,
    title,
    body,
    read: false,
    createdAt: Date.now(),
  });

  // Best-effort: the in-app Firestore record above is the source of truth; push is just a nudge
  // to a device that isn't currently looking at the app, so a failure here should never surface.
  sendPushToUser(space.ownerUid, {
    title,
    body,
    data: { type: "direct_chat", spaceId: space.id, personaId },
  }).catch((err) => console.error(`[tick] push notification failed for ${space.id}:`, err));
}

/**
 * When a Space has nothing queued, give it a spontaneous beat instead of sitting frozen: pick a
 * random persona and generate a short "what are they up to" activity from their own bio/mood, log
 * it to their memory, and occasionally move them -- so idle spaces still feel alive, satisfying
 * "start from something random with the available personas" rather than needing the user to
 * always be the one who kicks things off.
 */
async function generateSpontaneousActivity(
  space: SpaceDoc,
  llmConfig: { baseUrl: string; model: string }
): Promise<void> {
  const personas = await getAllPersonaDocs(space.id);
  if (personas.length === 0) return;

  const persona = personas[Math.floor(Math.random() * personas.length)];
  const ctx = await toPromptContext(persona, space, persona.emotionsTowardUser);

  try {
    const activity = await chatComplete(
      [
        buildPersonaSystemPrompt(ctx),
        {
          role: "user",
          content:
            "(Narrator note: nothing in particular is happening right now. In one short sentence, third person, " +
            "describe one small, true-to-character thing you're doing or thinking about at this moment.)",
        },
      ],
      { baseUrl: llmConfig.baseUrl, model: llmConfig.model }
    );

    await appendActivityLogEntry(
      space.id,
      persona.id,
      persona.name,
      "chat",
      activity,
      persona.currentPlaceId ? { id: persona.currentPlaceId, name: persona.currentPlaceName } : undefined
    );

    const places = await getPlaces(space.id);
    if (places.length > 0 && Math.random() < 0.2) {
      const candidates = places.filter((p) => p.id !== persona.currentPlaceId);
      const destination = candidates.length > 0 ? candidates[Math.floor(Math.random() * candidates.length)] : null;
      if (destination) await movePersonaToPlace(space.id, persona.id, persona.name, destination);
    }
  } catch (err) {
    console.error(`[tick] spontaneous activity failed for space ${space.id}:`, err);
  }
}

/**
 * The core orchestrator tick -- for each running Space, advances every due Story Feed task (or
 * generates a spontaneous beat if none are due).
 */
export async function tick(): Promise<void> {
  const runningSpaces = await firestore.collection("spaces").where("simStatus", "==", "running").get();
  for (const doc of runningSpaces.docs) {
    const space = { id: doc.id, ...(doc.data() as Omit<SpaceDoc, "id">) };
    try {
      await processSpace(space);
    } catch (err) {
      console.error(`[tick] space ${space.id} failed:`, err);
    }
  }
}
