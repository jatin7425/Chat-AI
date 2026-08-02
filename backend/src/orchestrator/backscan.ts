import { detectCommitment } from "./commitmentDetector";
import {
  createNeedsInput,
  createStoryFeedTask,
  getAllPersonaDocs,
  getRecentDirectMessages,
  hasSimilarPendingTask,
  resolveDelegationTarget,
} from "../services/chatService";

/**
 * Runs once whenever a Space's simulation is started: scans every persona's most recent direct
 * message for an unfulfilled commitment that predates this feature (or just wasn't caught live --
 * e.g. an assistant reply the user never got a live-detection pass on), and queues a StoryFeed
 * task for it with elevated priority, since these are user-visible existing promises rather than
 * ones the orchestrator merely inferred moments ago.
 */
export async function backscanPendingCommitments(
  spaceId: string,
  llmConfig: { baseUrl: string; model: string }
): Promise<void> {
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
        await createStoryFeedTask(spaceId, {
          description: `${persona.name} still needs to reach out to ${target.name}`,
          speakerPersonaId: persona.id,
          targetPersonaName: target.name,
          targetPersonaId: target.id,
          relatedChatPersonaId: persona.id,
          topic: result.topic,
          kind: "delegate",
          priority: 10,
        });
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
        await createStoryFeedTask(spaceId, {
          description: `${persona.name} still needs to follow up with you about: ${result.topic}`,
          speakerPersonaId: persona.id,
          targetPersonaName: "",
          targetPersonaId: null,
          relatedChatPersonaId: persona.id,
          topic: result.topic,
          kind: "self_followup",
          priority: 10,
        });
      }
    } catch (err) {
      console.error(`[backscan] failed for persona ${persona.id} in space ${spaceId}:`, err);
    }
  }
}
