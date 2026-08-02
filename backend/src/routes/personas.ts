import { Router } from "express";
import multer from "multer";
import { config } from "../config";
import { requireAuth } from "../middleware/authMiddleware";
import { analyzeAppearance } from "../llm/visionClient";
import { syncSpaceRelationships } from "../orchestrator/relationshipSync";
import { assertOwnership, ForbiddenError, NotFoundError } from "../services/spacesService";
import { uploadImage } from "../services/r2Service";
import { getUserLlmConfig } from "../services/usersService";
import { getPersonaDoc, getPlaces, movePersonaToPlace } from "../services/chatService";

export const personasRouter = Router();

interface MovePersonaBody {
  placeId: string;
}

personasRouter.post("/spaces/:spaceId/personas/:personaId/move", requireAuth, async (req, res) => {
  const { spaceId, personaId } = req.params;
  const uid = res.locals.uid as string;
  const { placeId } = (req.body ?? {}) as MovePersonaBody;

  if (!placeId) {
    res.status(400).json({ error: "placeId is required" });
    return;
  }

  try {
    await assertOwnership(spaceId, uid);
    const persona = await getPersonaDoc(spaceId, personaId);
    if (!persona) {
      res.status(404).json({ error: `Persona ${personaId} not found` });
      return;
    }
    const places = await getPlaces(spaceId);
    const place = places.find((p) => p.id === placeId);
    if (!place) {
      res.status(404).json({ error: `Place ${placeId} not found` });
      return;
    }
    await movePersonaToPlace(spaceId, personaId, persona.name, place);
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
    res.status(500).json({ error: err instanceof Error ? err.message : "Move failed" });
  }
});

/**
 * Refreshes every persona-to-persona relationship label in a Space (relationshipsToOtherPersonas
 * on each persona's profile) to reflect how they currently feel about each other, based on the
 * emotions that have accumulated from their actual interactions -- lets relationships evolve
 * instead of staying frozen at whatever was typed in at persona-creation time.
 */
personasRouter.post("/spaces/:spaceId/personas/sync-relationships", requireAuth, async (req, res) => {
  const { spaceId } = req.params;
  const uid = res.locals.uid as string;

  try {
    await assertOwnership(spaceId, uid);
    const llmConfig = await getUserLlmConfig(uid);
    const result = await syncSpaceRelationships(spaceId, llmConfig);
    res.json(result);
  } catch (err) {
    if (err instanceof NotFoundError) {
      res.status(404).json({ error: err.message });
      return;
    }
    if (err instanceof ForbiddenError) {
      res.status(403).json({ error: err.message });
      return;
    }
    res.status(500).json({ error: err instanceof Error ? err.message : "Relationship sync failed" });
  }
});

const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 5 * 1024 * 1024 } });

personasRouter.post(
  "/spaces/:spaceId/personas/:personaId/images/:kind",
  requireAuth,
  upload.single("image"),
  async (req, res) => {
    const { spaceId, personaId, kind } = req.params;
    const uid = res.locals.uid as string;

    if (kind !== "avatar" && kind !== "background" && kind !== "portfolio") {
      res.status(400).json({ error: "kind must be 'avatar', 'background', or 'portfolio'" });
      return;
    }
    if (!req.file) {
      res.status(400).json({ error: "image file is required" });
      return;
    }

    try {
      await assertOwnership(spaceId, uid);
      const key = `spaces/${spaceId}/personas/${personaId}/${kind}-${Date.now()}.jpg`;
      const url = await uploadImage(key, req.file.buffer, req.file.mimetype || "image/jpeg");
      res.json({ url });
    } catch (err) {
      if (err instanceof NotFoundError) {
        res.status(404).json({ error: err.message });
        return;
      }
      if (err instanceof ForbiddenError) {
        res.status(403).json({ error: err.message });
        return;
      }
      res.status(500).json({ error: err instanceof Error ? err.message : "Image upload failed" });
    }
  }
);

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
    const llmConfig = await getUserLlmConfig(uid);
    const appearance = await analyzeAppearance(imageBase64, mimeType || "image/jpeg", {
      baseUrl: llmConfig.baseUrl,
      model: config.llmVisionModel || llmConfig.model,
    });
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
