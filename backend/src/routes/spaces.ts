import { Router } from "express";
import { requireAuth } from "../middleware/authMiddleware";
import { firestore } from "../firebaseAdmin";
import { pingLlmServer } from "../llm/llmClient";
import { backscanPendingCommitments } from "../orchestrator/backscan";
import { dismissNeedsInput } from "../services/chatService";
import { assertOwnership, deleteSpaceRecursive, ForbiddenError, NotFoundError } from "../services/spacesService";
import { getUserLlmConfig } from "../services/usersService";

export const spacesRouter = Router();

/**
 * Starting a Space's simulation goes through the backend (not a direct client Firestore write)
 * specifically so it can backscan existing conversations for commitments that were never
 * actually followed through on -- e.g. a task the user gave a persona before this session, or a
 * promise that predates the commitment-detection feature -- and queue them with priority, so
 * "start simulation" also means "catch up on everything that's owed" rather than only reacting
 * to things going forward.
 */
spacesRouter.post("/spaces/:spaceId/start", requireAuth, async (req, res) => {
  const { spaceId } = req.params;
  const uid = res.locals.uid as string;

  try {
    await assertOwnership(spaceId, uid);
    await firestore.collection("spaces").doc(spaceId).update({ simStatus: "running", updatedAt: Date.now() });

    try {
      const llmConfig = await getUserLlmConfig(uid);
      // Wake the LLM server (often a free-tier host like Render that spins down when idle) and
      // wait for it to actually respond BEFORE the backscan and the tick loop start hammering it
      // with real calls -- otherwise the first several ticks just fail against a still-sleeping
      // server while it cold-starts, which is exactly what "simulation isn't doing anything for
      // the first couple minutes" looks like from the outside.
      await pingLlmServer(llmConfig.baseUrl);
      await backscanPendingCommitments(spaceId, llmConfig);
    } catch (err) {
      // No LLM configured yet, or the scan itself failed -- the simulation is still running,
      // this just means nothing gets backfilled until the user configures one.
      console.error(`[spaces] backscan failed for ${spaceId}:`, err);
    }

    res.json({ ok: true });
  } catch (err) {
    if (err instanceof NotFoundError) {
      res.status(404).json({ error: err.message });
      return;
    }
    if (err instanceof ForbiddenError) {
      res.status(403).json({ error: err.message });
      return;
    }
    res.status(500).json({ error: "Failed to start simulation" });
  }
});

/**
 * Lets the user tell a "needs your input" card that the extracted name was never a real persona
 * (a role reference like "the executive" that just didn't resolve to anyone) -- dismisses the
 * card and closes out the matching blocked storyFeed task so the tick loop stops retrying a
 * lookup that will never succeed, instead of leaving it stuck "blocked" forever.
 */
spacesRouter.post("/spaces/:spaceId/needs-input/:requestId/dismiss", requireAuth, async (req, res) => {
  const { spaceId, requestId } = req.params;
  const uid = res.locals.uid as string;

  try {
    await assertOwnership(spaceId, uid);
    await dismissNeedsInput(spaceId, requestId);
    res.json({ ok: true });
  } catch (err) {
    if (err instanceof NotFoundError) {
      res.status(404).json({ error: err.message });
      return;
    }
    if (err instanceof ForbiddenError) {
      res.status(403).json({ error: err.message });
      return;
    }
    res.status(500).json({ error: "Failed to dismiss" });
  }
});

// Firestore has no cascade delete, and a Space owns several subcollections (personas, chats,
// storyFeed, needsInput, npcTranscripts). This goes through the backend (Admin SDK
// recursiveDelete) rather than the client SDK -- see firestore.rules, which denies client-side
// space deletes entirely.
spacesRouter.delete("/spaces/:spaceId", requireAuth, async (req, res) => {
  const { spaceId } = req.params;
  const uid = res.locals.uid as string;

  try {
    await assertOwnership(spaceId, uid);
    await deleteSpaceRecursive(spaceId);
    res.status(204).send();
  } catch (err) {
    if (err instanceof NotFoundError) {
      res.status(404).json({ error: err.message });
      return;
    }
    if (err instanceof ForbiddenError) {
      res.status(403).json({ error: err.message });
      return;
    }
    res.status(500).json({ error: "Failed to delete space" });
  }
});
