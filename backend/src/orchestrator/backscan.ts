import { detectCommitment } from "./commitmentDetector";
import { runSelfFollowup, startExchange, SpaceDoc } from "./exchange";
import {
  createNeedsInput,
  createStoryFeedTask,
  getAllPersonaDocs,
  getRecentDirectMessages,
  getStoryFeedTask,
  hasSimilarPendingTask,
  resolveDelegationTarget,
} from "../services/chatService";

/**
 * Runs once whenever background chatter is (re-)enabled for a Space: scans every persona's most
 * recent direct message for an unfulfilled commitment that predates this feature (or just wasn't
 * caught live -- e.g. an assistant reply the user never got a live-detection pass on), and kicks
 * off a StoryFeed task for it immediately with elevated priority, since these are user-visible
 * existing promises rather than ones the orchestrator merely inferred moments ago. Called right
 * after the caller has already flipped the Space to simStatus "running", so every delegate task
 * found here starts its exchange chain immediately.
 */
export async function backscanPendingCommitments(
  space: SpaceDoc,
  llmConfig: { baseUrl: string; model: string }
): Promise<void> {
  const spaceId = space.id;
  const personas = await getAllPersonaDocs(spaceId);

  for (const persona of personas) {
    try {
      const history = await getRecentDirectMessages(spaceId, persona.id, 1);
      const lastMessage = history[history.length - 1];
      if (!lastMessage || lastMessage.role !== "assistant") continue;

      const result = await detectCommitment(lastMessage.content, llmConfig);
      if (!result.commits) continue;
      if (await hasSimilarPendingTask(spaceId, persona.id, result.topic)) continue;

      const { target, isSelfReference } = result.targetName
        ? await resolveDelegationTarget(spaceId, result.targetName, persona.id)
        : { target: null, isSelfReference: false };

      if (target) {
        const taskId = await createStoryFeedTask(spaceId, {
          description: `${persona.name} still needs to reach out to ${target.name}`,
          speakerPersonaId: persona.id,
          targetPersonaName: target.name,
          targetPersonaId: target.id,
          relatedChatPersonaId: persona.id,
          topic: result.topic,
          kind: "delegate",
          priority: 10,
        });
        startExchange(spaceId, taskId);
      } else if (result.targetName && !isSelfReference) {
        await createStoryFeedTask(spaceId, {
          description: `${persona.name} still needs to reach ${result.targetName}, who doesn't exist yet`,
          speakerPersonaId: persona.id,
          targetPersonaName: result.targetName,
          targetPersonaId: null,
          relatedChatPersonaId: persona.id,
          topic: result.topic,
          kind: "delegate",
          priority: 10,
        });
        await createNeedsInput(
          spaceId,
          `${persona.name} needs to know who ${result.targetName} is to reach out to them. Create this persona?`,
          result.targetName,
          persona.id
        );
      } else {
        const taskId = await createStoryFeedTask(spaceId, {
          description: `${persona.name} still needs to follow up with you about: ${result.topic}`,
          speakerPersonaId: persona.id,
          targetPersonaName: "",
          targetPersonaId: null,
          relatedChatPersonaId: persona.id,
          topic: result.topic,
          kind: "self_followup",
          priority: 10,
        });
        const task = await getStoryFeedTask(spaceId, taskId);
        if (task) await runSelfFollowup(space, task, llmConfig);
      }
    } catch (err) {
      console.error(`[backscan] failed for persona ${persona.id} in space ${spaceId}:`, err);
    }
  }
}
