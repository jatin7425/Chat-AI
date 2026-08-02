import { Router } from "express";
import { requireAuth } from "../middleware/authMiddleware";
import { firestore } from "../firebaseAdmin";
import { pingLlmServer } from "../llm/llmClient";
import { backscanPendingCommitments } from "../orchestrator/backscan";
import { maybeGenerateSpontaneousActivity } from "../orchestrator/exchange";
import { dismissNeedsInput } from "../services/chatService";
import { runInBackground } from "../services/backgroundWork";
import { assertOwnership, deleteSpaceRecursive, ForbiddenError, NotFoundError } from "../services/spacesService";
import { getUserLlmConfig } from "../services/usersService";

const SPONTANEOUS_ACTIVITY_DEBOUNCE_MS = 30 * 60 * 1000; // 30 minutes

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
    const space = await assertOwnership(spaceId, uid);
    await firestore.collection("spaces").doc(spaceId).update({ simStatus: "running", updatedAt: Date.now() });

    try {
      const llmConfig = await getUserLlmConfig(uid);
      // Wake the LLM server (often a free-tier host like Render that spins down when idle) and
      // wait for it to actually respond BEFORE the backscan starts hammering it with real calls --
      // otherwise the first several exchange steps just fail against a still-sleeping server while
      // it cold-starts, which is exactly what "nothing's happening" looks like from the outside.
      await pingLlmServer(llmConfig.baseUrl);
      await backscanPendingCommitments({ ...space, simStatus: "running" }, llmConfig);
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

/**
 * Called by the app whenever the user opens a Space -- the event-driven replacement for the old
 * "nothing was due this tick" idle heartbeat. Debounced via lastSpontaneousAt so re-opening the
 * same Space repeatedly doesn't spam an LLM call every time; fire-and-forget from the client's
 * perspective, this always responds immediately regardless of whether anything actually fired.
 */
spacesRouter.post("/spaces/:spaceId/view", requireAuth, async (req, res) => {
  const { spaceId } = req.params;
  const uid = res.locals.uid as string;

  try {
    const space = await assertOwnership(spaceId, uid);
    const dueForSpontaneous = Date.now() - (space.lastSpontaneousAt ?? 0) > SPONTANEOUS_ACTIVITY_DEBOUNCE_MS;

    if (dueForSpontaneous) {
      await firestore.collection("spaces").doc(spaceId).update({ lastSpontaneousAt: Date.now() });
      runInBackground(async () => {
        const llmConfig = await getUserLlmConfig(uid);
        await maybeGenerateSpontaneousActivity(space, llmConfig);
      });
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
    res.status(500).json({ error: "Failed to register space view" });
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
