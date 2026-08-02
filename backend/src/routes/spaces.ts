import { Router } from "express";
import { requireAuth } from "../middleware/authMiddleware";
import { assertOwnership, deleteSpaceRecursive, ForbiddenError, NotFoundError } from "../services/spacesService";

export const spacesRouter = Router();

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
