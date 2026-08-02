import { Router } from "express";
import { requireAuth } from "../middleware/authMiddleware";
import { analyzeAppearance } from "../llm/visionClient";
import { assertOwnership, ForbiddenError, NotFoundError } from "../services/spacesService";

export const personasRouter = Router();

interface AnalyzePhotoBody {
  imageBase64: string;
  mimeType?: string;
}

personasRouter.post("/spaces/:spaceId/personas/analyze-photo", requireAuth, async (req, res) => {
  const { spaceId } = req.params;
  const uid = res.locals.uid as string;
  const { imageBase64, mimeType } = (req.body ?? {}) as AnalyzePhotoBody;

  if (!imageBase64) {
    res.status(400).json({ error: "imageBase64 is required" });
    return;
  }

  try {
    await assertOwnership(spaceId, uid);
    const appearance = await analyzeAppearance(imageBase64, mimeType || "image/jpeg");
    res.json(appearance);
  } catch (err) {
    if (err instanceof NotFoundError) {
      res.status(404).json({ error: err.message });
      return;
    }
    if (err instanceof ForbiddenError) {
      res.status(403).json({ error: err.message });
      return;
    }
    res.status(500).json({ error: err instanceof Error ? err.message : "Photo analysis failed" });
  }
});
