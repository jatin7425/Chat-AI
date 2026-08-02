import { firestore } from "../firebaseAdmin";
import { chatComplete, ChatMessage } from "../llm/llmClient";
import { config } from "../config";
import { detectCommitment } from "./commitmentDetector";
import { detectNewPlaceMention } from "./placeDetector";
import { sendPushToUser } from "../services/fcmService";
import { runInBackground } from "../services/backgroundWork";
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
  getNpcTranscriptMessages,
  getPersonaDoc,
  getPersonaMemory,
  getPlaces,
  getStoryFeedTask,
  hasSimilarPendingTask,
  movePersonaToPlace,
  resolveDelegationTarget,
  resolveNeedsInputForName,
  updatePersonaEmotions,
  updatePersonaEmotionsTowardPersona,
  RelationshipEmotions,
  PersonaDoc,
  StoryFeedTaskDoc,
} from "../services/chatService";
import { buildNpcTurnSystemPrompt, buildPersonaSystemPrompt, PersonaPromptContext } from "./promptBuilder";

const EMPTY_EMOTIONS: RelationshipEmotions = { affection: 0, love: 0, lust: 0, trust: 0 };

/** A delegate exchange runs at most this many single-line turns before it's forced to wrap up -- "chat with another persona for a bit, then close it out," not an open-ended simulation. */
const MAX_EXCHANGE_TURNS = 10;

export interface SpaceDoc {
  id: string;
  ownerUid: string;
  name: string;
  premise: string;
  simDate: string;
  simStatus?: "running" | "paused";
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
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

/**
 * Fires the next step of a bounded exchange as a fresh serverless invocation, via this same
 * deployment's own /api/internal/advance-exchange endpoint -- this is how the exchange keeps
 * itself going without a polling loop. Wrapped in runInBackground (Vercel's waitUntil) so the
 * dispatch itself survives even though the caller doesn't await the result.
 */
function triggerNextExchangeStep(spaceId: string, taskId: string): void {
  runInBackground(async () => {
    await fetch(`${config.internalBaseUrl}/api/internal/advance-exchange`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Internal-Secret": config.internalTaskSecret },
      body: JSON.stringify({ spaceId, taskId }),
    });
  });
}

/** Kicks off a freshly-created delegate task's exchange chain -- the event-driven equivalent of "wait for the next tick to notice this task." */
export function startExchange(spaceId: string, taskId: string): void {
  triggerNextExchangeStep(spaceId, taskId);
}

/** Best-effort: detects a commitment in a line and spawns a cascading StoryFeed task -- lets claims made WITHIN a persona-to-persona exchange also become real, same as a direct-chat reply. Bounded to running once per exchange (its concluding line) rather than every turn. */
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
      const taskId = await createStoryFeedTask(space.id, {
        description: `${speakerPersona.name} is planning to reach out to ${target.name}`,
        speakerPersonaId: speakerPersona.id,
        targetPersonaName: target.name,
        targetPersonaId: target.id,
        relatedChatPersonaId: speakerPersona.id,
        topic: result.topic,
        kind: "delegate",
      });
      if (space.simStatus === "running") startExchange(space.id, taskId);
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
      const taskId = await createStoryFeedTask(space.id, {
        description: `${speakerPersona.name} needs to follow up with you about: ${result.topic}`,
        speakerPersonaId: speakerPersona.id,
        targetPersonaName: "",
        targetPersonaId: null,
        relatedChatPersonaId: speakerPersona.id,
        topic: result.topic,
        kind: "self_followup",
      });
      const task = await getStoryFeedTask(space.id, taskId);
      const llm = llmConfig;
      if (task) runInBackground(() => runSelfFollowup(space, task, llm));
    }
  } catch (err) {
    console.error(`[exchange] commitment follow-up failed for ${speakerPersona.name}:`, err);
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
    console.error(`[exchange] place detection failed for space ${space.id}:`, err);
  }
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
  sendPushToUser(space.ownerUid, {
    title,
    body,
    data: { type: "direct_chat", spaceId: space.id, personaId },
  }).catch((err) => console.error(`[exchange] push notification failed for ${space.id}:`, err));
}

/**
 * Runs a self-followup task: the same persona who made a bare commitment ("I'll get back to you")
 * reports back to the user directly, once, after a short pacing delay so it doesn't read as an
 * instant reflex to their own promise.
 */
export async function runSelfFollowup(
  space: SpaceDoc,
  task: StoryFeedTaskDoc,
  llmConfig: { baseUrl: string; model: string }
): Promise<void> {
  const storyFeedDocRef = firestore.collection("spaces").doc(space.id).collection("storyFeed").doc(task.id);
  const speaker = await getPersonaDoc(space.id, task.speakerPersonaId);
  if (!speaker) {
    await storyFeedDocRef.update({ status: "blocked", blockedReason: "The persona was deleted", updatedAt: Date.now() });
    return;
  }
  await storyFeedDocRef.update({ status: "in_progress", blockedReason: null, updatedAt: Date.now() });
  await sleep(3000 + Math.random() * 7000);

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
}

/**
 * Runs exactly one turn of a bounded persona-to-persona exchange (one line, from whoever's turn
 * it is), then either concludes the exchange (report back to the user, wrap-up side effects) or
 * triggers the next turn as a fresh invocation. This is the entire "orchestrator loop" now --
 * there's no shared queue to poll, each task drives itself to completion via this chain.
 */
export async function runSingleExchangeStep(
  space: SpaceDoc,
  task: StoryFeedTaskDoc,
  llmConfig: { baseUrl: string; model: string }
): Promise<void> {
  const storyFeedDocRef = firestore.collection("spaces").doc(space.id).collection("storyFeed").doc(task.id);

  if (task.status === "done") return; // already concluded (e.g. a duplicate/late self-chain call)

  let targetPersonaId = task.targetPersonaId;
  if (!targetPersonaId) {
    const resolved = await findPersonaByName(space.id, task.targetPersonaName);
    if (!resolved) {
      await storyFeedDocRef.update({
        status: "blocked",
        blockedReason: `Persona '${task.targetPersonaName}' doesn't exist yet`,
        updatedAt: Date.now(),
      });
      return;
    }
    if (resolved.id === task.speakerPersonaId) {
      await storyFeedDocRef.update({
        status: "done",
        blockedReason: "Target resolved to the speaker themselves -- nothing to delegate",
        updatedAt: Date.now(),
      });
      return;
    }
    targetPersonaId = resolved.id;
    await resolveNeedsInputForName(space.id, task.targetPersonaName);
    await storyFeedDocRef.update({ targetPersonaId, updatedAt: Date.now() });
  }

  const speaker = await getPersonaDoc(space.id, task.speakerPersonaId);
  const target = await getPersonaDoc(space.id, targetPersonaId);
  if (!speaker || !target) {
    await storyFeedDocRef.update({ status: "blocked", blockedReason: "A participant persona was deleted", updatedAt: Date.now() });
    return;
  }

  await storyFeedDocRef.update({ status: "in_progress", blockedReason: null, updatedAt: Date.now() });

  const stepIndex = task.exchangeCount ?? 0;
  const isLastStep = stepIndex >= MAX_EXCHANGE_TURNS - 1;
  const isSpeakerTurn = stepIndex % 2 === 0;
  const currentTurnPersona = isSpeakerTurn ? speaker : target;
  const otherPersona = isSpeakerTurn ? target : speaker;

  const speakerCtx = await toPromptContext(speaker, space, speaker.emotionsTowardPersonas[target.id] ?? EMPTY_EMOTIONS);
  const targetCtx = await toPromptContext(target, space, target.emotionsTowardPersonas[speaker.id] ?? EMPTY_EMOTIONS);

  const priorLines = await getNpcTranscriptMessages(space.id, task.id);
  const history: ChatMessage[] = priorLines.map((line) => ({
    role: line.personaId === currentTurnPersona.id ? "assistant" : "user",
    content: line.text,
  }));

  const messages: ChatMessage[] = [
    buildNpcTurnSystemPrompt({ speaker: speakerCtx, target: targetCtx, topic: task.topic }, isSpeakerTurn),
    ...history,
  ];
  if (isLastStep) {
    messages.push({
      role: "user",
      content: "(Narrator note: this is the last exchange -- bring the conversation to a natural close in your next line.)",
    });
  }

  const line = await chatComplete(messages, { baseUrl: llmConfig.baseUrl, model: llmConfig.model });

  await appendNpcTranscriptMessage(space.id, task.id, currentTurnPersona.id, line);
  await appendActivityLogEntry(
    space.id,
    currentTurnPersona.id,
    currentTurnPersona.name,
    "chat",
    `Talked to ${otherPersona.name} about ${task.topic || "something"}`,
    currentTurnPersona.currentPlaceId ? { id: currentTurnPersona.currentPlaceId, name: currentTurnPersona.currentPlaceName } : undefined
  );

  const newExchangeCount = stepIndex + 1;
  await storyFeedDocRef.update({ exchangeCount: newExchangeCount, updatedAt: Date.now() });

  if (newExchangeCount < MAX_EXCHANGE_TURNS) {
    startExchange(space.id, task.id);
    return;
  }

  // Conclude: sentiment/mood drift, cascading commitment/place detection, occasional movement,
  // then report back to the user in the speaker's own voice.
  const fullTranscript = [...priorLines.map((l) => l.text), line].join(" ");
  const sentiment = coarseSentiment(fullTranscript);
  const newSpeakerMood = driftMood(speaker.mood, sentiment);
  const newTargetMood = driftMood(target.mood, sentiment);
  const speakerTowardTarget = driftEmotions(speaker.emotionsTowardPersonas[target.id] ?? EMPTY_EMOTIONS, sentiment);
  const targetTowardSpeaker = driftEmotions(target.emotionsTowardPersonas[speaker.id] ?? EMPTY_EMOTIONS, sentiment);

  await updatePersonaEmotions(space.id, speaker.id, { mood: newSpeakerMood });
  await updatePersonaEmotions(space.id, target.id, { mood: newTargetMood });
  await updatePersonaEmotionsTowardPersona(space.id, speaker.id, target.id, speakerTowardTarget);
  await updatePersonaEmotionsTowardPersona(space.id, target.id, speaker.id, targetTowardSpeaker);

  await followUpOnCommitment(space, currentTurnPersona, line, llmConfig);
  await followUpOnPlaceMention(space, fullTranscript, llmConfig);

  try {
    const places = await getPlaces(space.id);
    if (places.length > 0 && Math.random() < 0.25) {
      const mover = Math.random() < 0.5 ? speaker : target;
      const candidates = places.filter((p) => p.id !== mover.currentPlaceId);
      const destination = candidates.length > 0 ? candidates[Math.floor(Math.random() * candidates.length)] : null;
      if (destination) await movePersonaToPlace(space.id, mover.id, mover.name, destination);
    }
  } catch (err) {
    console.error(`[exchange] movement for space ${space.id} failed:`, err);
  }

  const reportBackPrompt = buildPersonaSystemPrompt({ ...speakerCtx, emotionsTowardUser: speaker.emotionsTowardUser });
  const reportBackText = await chatComplete(
    [
      reportBackPrompt,
      {
        role: "user",
        content: `(Narrator note: report back to me in 1-2 sentences, in character, that you talked to ${target.name} about: ${task.topic}.)`,
      },
    ],
    { baseUrl: llmConfig.baseUrl, model: llmConfig.model }
  );

  const delivered = await deliverOrQueueToUser(space.id, task.relatedChatPersonaId, reportBackText, `talked to ${target.name} about ${task.topic}`);
  if (delivered) await notifyUser(space, speaker.name, reportBackText, task.relatedChatPersonaId);

  await storyFeedDocRef.update({ status: "done", updatedAt: Date.now() });
}

/**
 * Fired when the user opens a Space (see routes/spaces.ts's /view route) -- gives idle Spaces a
 * spontaneous beat from a random persona's own bio/mood, the event-driven replacement for the old
 * "nothing was due this tick" heartbeat. Debouncing (don't fire on every single visit) is the
 * caller's responsibility via Space.lastSpontaneousAt.
 */
export async function maybeGenerateSpontaneousActivity(
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
    console.error(`[exchange] spontaneous activity failed for space ${space.id}:`, err);
  }
}
