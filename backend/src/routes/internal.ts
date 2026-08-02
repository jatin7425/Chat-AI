import { Router } from "express";
import { config } from "../config";
import { runSingleExchangeStep } from "../orchestrator/exchange";
import { getSpace } from "../services/spacesService";
import { getStoryFeedTask } from "../services/chatService";
import { getUserLlmConfig } from "../services/usersService";

export const internalRouter = Router();

interface AdvanceExchangeBody {
  spaceId?: string;
  taskId?: string;
}

/**
 * Server-to-server only -- this is how a bounded persona-to-persona exchange keeps itself going
 * across separate serverless invocations (see backgroundWork.ts and orchestrator/exchange.ts's
 * triggerNextExchangeStep). No Firebase user ever calls this directly, so it's gated by a shared
 * secret instead of requireAuth.
 */
internalRouter.post("/internal/advance-exchange", async (req, res) => {
  const secret = req.header("X-Internal-Secret");
  if (!secret || secret !== config.internalTaskSecret) {
    res.status(401).json({ error: "Invalid internal secret" });
    return;
  }

  const { spaceId, taskId } = (req.body ?? {}) as AdvanceExchangeBody;
  if (!spaceId || !taskId) {
    res.status(400).json({ error: "spaceId and taskId are required" });
    return;
  }

  try {
    const space = await getSpace(spaceId);
    const task = await getStoryFeedTask(spaceId, taskId);
    if (!space || !task) {
      res.status(404).json({ error: "Space or task not found" });
      return;
    }

    const llmConfig = await getUserLlmConfig(space.ownerUid);
    await runSingleExchangeStep(space, task, llmConfig);
    res.json({ ok: true });
  } catch (err) {
    console.error(`[internal] advance-exchange failed for task ${taskId} in space ${spaceId}:`, err);
    res.status(500).json({ error: err instanceof Error ? err.message : "Failed to advance exchange" });
  }
});
