import { Router } from "express";
import { requireAuth } from "../middleware/authMiddleware";
import { chatComplete } from "../llm/llmClient";
import { buildPersonaSystemPrompt } from "../orchestrator/promptBuilder";
import { detectCommitment } from "../orchestrator/commitmentDetector";
import { detectNewPlaceMention } from "../orchestrator/placeDetector";
import { runSelfFollowup, startExchange } from "../orchestrator/exchange";
import { sendPushToUser } from "../services/fcmService";
import { runInBackground } from "../services/backgroundWork";
import {
  appendActivityLogEntry,
  appendCoreMemory,
  appendDirectMessage,
  coarseSentiment,
  createNeedsInput,
  createPlace,
  createStoryFeedTask,
  driftEmotions,
  driftMood,
  findPlaceByName,
  getPersonaDoc,
  getPersonaMemory,
  getPlaces,
  getRecentDirectMessages,
  getStoryFeedTask,
  hasSimilarPendingTask,
  popPendingTopic,
  resolveDelegationTarget,
  setAwaitingUserReply,
  updatePersonaEmotions,
  PersonaDoc,
} from "../services/chatService";
import { assertOwnership, ForbiddenError, getAllPersonas, NotFoundError, SpaceDoc } from "../services/spacesService";
import { getUserLlmConfig } from "../services/usersService";

export const messagesRouter = Router();

interface SendMessageBody {
  text: string;
}

/**
 * Generates the persona's reply from whatever direct-chat history already exists (the caller is
 * responsible for making sure the latest user message is already in Firestore), appends it,
 * logs the activity entry, drifts mood/feelings, and kicks off best-effort commitment/milestone/
 * place detection. Shared by the normal send route and the retry route -- retry must never
 * re-append the user's message, only regenerate the reply for it.
 */
async function generateAndAppendReply(
  space: SpaceDoc,
  persona: PersonaDoc,
  llmConfig: { baseUrl: string; model: string },
  latestUserText: string
): Promise<string> {
  const history = await getRecentDirectMessages(space.id, persona.id, 12);

  // Expose the persona's known relationships with OTHER personas (already collected via the
  // Relationships section in the app's persona editor) so the model doesn't act clueless when
  // the user brings someone up by name -- without this, every persona had zero disclosed
  // context about anyone but the user, causing exactly that "who is X?" confusion.
  const allPersonas = await getAllPersonas(space.id);
  const nameById = new Map(allPersonas.map((p: any) => [p.id, p.name as string]));
  const knownPersonas = Object.entries(persona.relationshipsToOtherPersonas)
    .map(([otherId, relationship]) => ({ name: nameById.get(otherId) ?? "", relationship }))
    .filter((p) => p.name);

  const recentMemory = await getPersonaMemory(space.id, persona.id, 20);

  // If this persona has been meaning to bring something up (queued because they already sent an
  // unanswered proactive message earlier), work exactly one of those into this reply -- draining
  // the queue one topic per user turn rather than dumping everything on them at once.
  const queuedTopic = await popPendingTopic(space.id, persona.id);

  const systemPrompt = buildPersonaSystemPrompt({
    name: persona.name,
    background: persona.background,
    bio: persona.bio,
    appearance: persona.appearance,
    mood: persona.mood,
    aggressiveness: persona.aggressiveness,
    relationshipToUser: persona.relationshipToUser,
    emotionsTowardUser: persona.emotionsTowardUser,
    spacePremise: space.premise,
    spaceSimDate: space.simDate,
    knownPersonas,
    coreMemories: persona.coreMemories,
    recentMemory,
  });
  if (queuedTopic) {
    systemPrompt.content += `\nThere's something you've been meaning to bring up with the user: ${queuedTopic}. Naturally work this into your reply.`;
  }

  const reply = await chatComplete([systemPrompt, ...history], {
    baseUrl: llmConfig.baseUrl,
    model: llmConfig.model,
  });

  await appendDirectMessage(space.id, persona.id, "assistant", reply);
  // The user just engaged, so any earlier unanswered proactive ping is resolved now.
  await setAwaitingUserReply(space.id, persona.id, false);

  // Best-effort push -- the request that triggered this reply is itself proof the user was just
  // in this chat, so a push would almost always be redundant; the client-side suppression check
  // in SpacesFcmService also gates on the chat being active, but sending unconditionally here
  // keeps the backend simple and correct for the (rarer) case where the reply lands after the
  // user has already navigated away.
  runInBackground(async () => {
    await sendPushToUser(space.ownerUid, {
      title: persona.name,
      body: reply.length > 120 ? `${reply.slice(0, 117)}...` : reply,
      data: { type: "direct_chat", spaceId: space.id, personaId: persona.id },
    });
  });

  await appendActivityLogEntry(
    space.id,
    persona.id,
    persona.name,
    "chat",
    `Chatted with you${latestUserText.length > 0 ? ` about "${latestUserText.slice(0, 60)}"` : ""}`,
    persona.currentPlaceId ? { id: persona.currentPlaceId, name: persona.currentPlaceName } : undefined
  );

  // Mood/feelings drift from direct chat too, not just background NPC exchanges -- otherwise
  // talking to the user directly never actually affected how a persona feels about them.
  const sentiment = coarseSentiment(`${latestUserText} ${reply}`);
  await updatePersonaEmotions(space.id, persona.id, {
    mood: driftMood(persona.mood, sentiment),
    emotionsTowardUser: driftEmotions(persona.emotionsTowardUser, sentiment),
  });

  // Best-effort: none of this should ever break the reply the user is actually waiting on, so it
  // all runs in the background after the response-critical work and swallows its own errors.
  runInBackground(async () => {
    const result = await detectCommitment(reply, llmConfig);
    if (result.isMilestone && result.milestoneNote) {
      await appendCoreMemory(space.id, persona.id, `${result.milestoneNote} (${space.simDate || "undated"})`);
    }
    if (!result.commits) return;
    if (await hasSimilarPendingTask(space.id, persona.id, result.topic)) return;

    const { target, isSelfReference } = result.targetName
      ? await resolveDelegationTarget(space.id, result.targetName, persona.id)
      : { target: null, isSelfReference: false };

    if (target) {
      // Delegate handoff -- only actually starts the persona-to-persona exchange chain if
      // background chatter is enabled for this Space; either way the user's own reply above
      // already happened, this is purely about whether personas also talk to each other.
      const taskId = await createStoryFeedTask(space.id, {
        description: `${persona.name} is planning to reach out to ${target.name}`,
        speakerPersonaId: persona.id,
        targetPersonaName: target.name,
        targetPersonaId: target.id,
        relatedChatPersonaId: persona.id,
        topic: result.topic || latestUserText,
        kind: "delegate",
      });
      if (space.simStatus === "running") startExchange(space.id, taskId);
    } else if (result.targetName && !isSelfReference) {
      await createStoryFeedTask(space.id, {
        description: `${persona.name} needs to reach ${result.targetName}, who doesn't exist yet`,
        speakerPersonaId: persona.id,
        targetPersonaName: result.targetName,
        targetPersonaId: null,
        relatedChatPersonaId: persona.id,
        topic: result.topic || latestUserText,
        kind: "delegate",
      });
      await createNeedsInput(
        space.id,
        `${persona.name} needs to know who ${result.targetName} is to reach out to them. Create this persona?`,
        result.targetName,
        persona.id
      );
    } else {
      // A bare commitment with no named other party ("I'll get back to you"), or one that
      // resolved back to the persona themselves -- either way, the same persona follows up with
      // the user directly, not a delegate handoff, so it always runs regardless of the Space's
      // background-chatter switch.
      const taskId = await createStoryFeedTask(space.id, {
        description: `${persona.name} needs to follow up with you about: ${result.topic}`,
        speakerPersonaId: persona.id,
        targetPersonaName: "",
        targetPersonaId: null,
        relatedChatPersonaId: persona.id,
        topic: result.topic,
        kind: "self_followup",
      });
      const task = await getStoryFeedTask(space.id, taskId);
      if (task) await runSelfFollowup(space, task, llmConfig);
    }
  });

  runInBackground(async () => {
    const existingPlaceNames = (await getPlaces(space.id)).map((p) => p.name);
    const result = await detectNewPlaceMention(`${latestUserText} ${reply}`, existingPlaceNames, llmConfig);
    if (!result.mentionsNewPlace) return;
    if (await findPlaceByName(space.id, result.name)) return;
    await createPlace(space.id, result.name, result.description);
  });

  return reply;
}

messagesRouter.post("/spaces/:spaceId/personas/:personaId/messages", requireAuth, async (req, res) => {
  const { spaceId, personaId } = req.params;
  const uid = res.locals.uid as string;
  const { text } = (req.body ?? {}) as SendMessageBody;

  if (!text || !text.trim()) {
    res.status(400).json({ error: "text is required" });
    return;
  }

  try {
    const space = await assertOwnership(spaceId, uid);
    const persona = await getPersonaDoc(spaceId, personaId);
    if (!persona) {
      res.status(404).json({ error: `Persona ${personaId} not found` });
      return;
    }
    const llmConfig = await getUserLlmConfig(uid);

    await appendDirectMessage(spaceId, personaId, "user", text.trim());

    const reply = await generateAndAppendReply(space, persona, llmConfig, text.trim());

    res.json({ reply });
  } catch (err) {
    if (err instanceof NotFoundError) {
      res.status(404).json({ error: err.message });
      return;
    }
    if (err instanceof ForbiddenError) {
      res.status(403).json({ error: err.message });
      return;
    }
    res.status(500).json({ error: err instanceof Error ? err.message : "Failed to send message" });
  }
});

/**
 * Regenerates the persona's reply to the most recent user message, WITHOUT re-appending it --
 * for when the reply itself failed (LLM timeout/error) but the user's message already made it
 * into Firestore. 400s if the most recent message isn't actually an un-replied-to user message,
 * so this can't accidentally double-reply to something that already got an answer.
 */
messagesRouter.post("/spaces/:spaceId/personas/:personaId/messages/retry", requireAuth, async (req, res) => {
  const { spaceId, personaId } = req.params;
  const uid = res.locals.uid as string;

  try {
    const space = await assertOwnership(spaceId, uid);
    const persona = await getPersonaDoc(spaceId, personaId);
    if (!persona) {
      res.status(404).json({ error: `Persona ${personaId} not found` });
      return;
    }
    const llmConfig = await getUserLlmConfig(uid);

    const history = await getRecentDirectMessages(spaceId, personaId, 1);
    const lastMessage = history[history.length - 1];
    if (!lastMessage || lastMessage.role !== "user") {
      res.status(400).json({ error: "Nothing to retry -- the last message already has a reply." });
      return;
    }

    const reply = await generateAndAppendReply(space, persona, llmConfig, lastMessage.content);

    res.json({ reply });
  } catch (err) {
    if (err instanceof NotFoundError) {
      res.status(404).json({ error: err.message });
      return;
    }
    if (err instanceof ForbiddenError) {
      res.status(403).json({ error: err.message });
      return;
    }
    res.status(500).json({ error: err instanceof Error ? err.message : "Retry failed" });
  }
});
