import { Router } from "express";
import { requireAuth } from "../middleware/authMiddleware";
import { chatComplete } from "../llm/llmClient";
import { buildGroupPersonaSystemPrompt } from "../orchestrator/promptBuilder";
import { detectCommitment } from "../orchestrator/commitmentDetector";
import { detectNewPlaceMention } from "../orchestrator/placeDetector";
import { runSelfFollowup, startExchange } from "../orchestrator/exchange";
import { sendPushToUser } from "../services/fcmService";
import { runInBackground } from "../services/backgroundWork";
import {
  appendActivityLogEntry,
  appendCoreMemory,
  appendGroupChatMessage,
  coarseSentiment,
  createNeedsInput,
  createPlace,
  createStoryFeedTask,
  driftEmotions,
  driftMood,
  findPlaceByName,
  getGroupChat,
  getStoryFeedTask,
  resolveDelegationTarget,
  getPersonaDoc,
  getPersonaMemory,
  getPlaces,
  getRecentGroupChatMessages,
  hasSimilarPendingTask,
  toPersonaChatHistory,
  updatePersonaEmotions,
  PersonaDoc,
} from "../services/chatService";
import { assertOwnership, ForbiddenError, NotFoundError } from "../services/spacesService";
import { getUserLlmConfig } from "../services/usersService";

export const groupMessagesRouter = Router();

interface SendGroupMessageBody {
  text: string;
}

groupMessagesRouter.post("/spaces/:spaceId/group-chats/:groupChatId/messages", requireAuth, async (req, res) => {
  const { spaceId, groupChatId } = req.params;
  const uid = res.locals.uid as string;
  const { text } = (req.body ?? {}) as SendGroupMessageBody;

  if (!text || !text.trim()) {
    res.status(400).json({ error: "text is required" });
    return;
  }

  try {
    const space = await assertOwnership(spaceId, uid);
    const groupChat = await getGroupChat(spaceId, groupChatId);
    if (!groupChat) {
      res.status(404).json({ error: `Group chat ${groupChatId} not found` });
      return;
    }

    const personas: PersonaDoc[] = [];
    for (const personaId of groupChat.personaIds) {
      const persona = await getPersonaDoc(spaceId, personaId);
      if (persona) personas.push(persona);
    }
    if (personas.length === 0) {
      res.status(400).json({ error: "This group chat has no personas left in it" });
      return;
    }

    const llmConfig = await getUserLlmConfig(uid);
    const nameById = Object.fromEntries(personas.map((p) => [p.id, p.name]));
    const existingPlaceNames = (await getPlaces(spaceId)).map((p) => p.name);

    await appendGroupChatMessage(spaceId, groupChatId, "user", text.trim());

    // Each member replies in turn, sequentially -- every persona sees everyone's replies so far
    // in this same round (including earlier personas' replies to this same message), which is
    // what makes the group feel like it's actually reacting to each other, not just the user.
    const replies: Array<{ personaId: string; text: string }> = [];
    for (const persona of personas) {
      const history = await getRecentGroupChatMessages(spaceId, groupChatId, 30);
      const otherNames = personas.filter((p) => p.id !== persona.id).map((p) => p.name);
      const recentMemory = await getPersonaMemory(spaceId, persona.id, 20);
      const systemPrompt = buildGroupPersonaSystemPrompt(
        {
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
          coreMemories: persona.coreMemories,
          recentMemory,
        },
        otherNames
      );

      const reply = await chatComplete(
        [systemPrompt, ...toPersonaChatHistory(history, persona.id, nameById)],
        { baseUrl: llmConfig.baseUrl, model: llmConfig.model }
      );

      await appendGroupChatMessage(spaceId, groupChatId, "assistant", reply, persona.id);
      replies.push({ personaId: persona.id, text: reply });

      runInBackground(async () => {
        await sendPushToUser(space.ownerUid, {
          title: `${persona.name} in ${groupChat.name}`,
          body: reply.length > 120 ? `${reply.slice(0, 117)}...` : reply,
          data: { type: "group_chat", spaceId, groupChatId },
        });
      });

      await appendActivityLogEntry(
        spaceId,
        persona.id,
        persona.name,
        "chat",
        `Talked in "${groupChat.name}" about "${text.trim().slice(0, 60)}"`,
        persona.currentPlaceId ? { id: persona.currentPlaceId, name: persona.currentPlaceName } : undefined
      );

      const sentiment = coarseSentiment(`${text} ${reply}`);
      await updatePersonaEmotions(spaceId, persona.id, {
        mood: driftMood(persona.mood, sentiment),
        emotionsTowardUser: driftEmotions(persona.emotionsTowardUser, sentiment),
      });

      // Best-effort, same pattern as direct chat -- never blocks the replies the user is waiting on.
      runInBackground(async () => {
        const result = await detectCommitment(reply, llmConfig);
        if (result.isMilestone && result.milestoneNote) {
          await appendCoreMemory(spaceId, persona.id, `${result.milestoneNote} (${space.simDate || "undated"})`);
        }
        if (!result.commits) return;
        if (await hasSimilarPendingTask(spaceId, persona.id, result.topic)) return;

        const { target, isSelfReference } = result.targetName
          ? await resolveDelegationTarget(spaceId, result.targetName, persona.id)
          : { target: null, isSelfReference: false };

        if (target) {
          const taskId = await createStoryFeedTask(spaceId, {
            description: `${persona.name} is planning to reach out to ${target.name}`,
            speakerPersonaId: persona.id,
            targetPersonaName: target.name,
            targetPersonaId: target.id,
            relatedChatPersonaId: persona.id,
            topic: result.topic || text.trim(),
            kind: "delegate",
          });
          if (space.simStatus === "running") startExchange(spaceId, taskId);
        } else if (result.targetName && !isSelfReference) {
          await createStoryFeedTask(spaceId, {
            description: `${persona.name} needs to reach ${result.targetName}, who doesn't exist yet`,
            speakerPersonaId: persona.id,
            targetPersonaName: result.targetName,
            targetPersonaId: null,
            relatedChatPersonaId: persona.id,
            topic: result.topic || text.trim(),
            kind: "delegate",
          });
          await createNeedsInput(
            spaceId,
            `${persona.name} needs to know who ${result.targetName} is to reach out to them. Create this persona?`,
            result.targetName,
            persona.id
          );
        } else {
          const taskId = await createStoryFeedTask(spaceId, {
            description: `${persona.name} needs to follow up with you about: ${result.topic}`,
            speakerPersonaId: persona.id,
            targetPersonaName: "",
            targetPersonaId: null,
            relatedChatPersonaId: persona.id,
            topic: result.topic,
            kind: "self_followup",
          });
          const task = await getStoryFeedTask(spaceId, taskId);
          if (task) await runSelfFollowup(space, task, llmConfig);
        }
      });

      runInBackground(async () => {
        const result = await detectNewPlaceMention(`${text} ${reply}`, existingPlaceNames, llmConfig);
        if (!result.mentionsNewPlace) return;
        if (await findPlaceByName(spaceId, result.name)) return;
        await createPlace(spaceId, result.name, result.description);
      });
    }

    res.json({ replies });
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
