import { Router } from "express";
import { requireAuth } from "../middleware/authMiddleware";
import { upsertUserProfile } from "../services/usersService";

export const usersRouter = Router();

interface SyncUserBody {
  displayName?: string;
}

// Called by the mobile app right after any successful auth (email sign-up, email sign-in,
// or Google SSO) and again on cold-start rehydration of an existing session -- keeps
// users/{uid} in Firestore in sync regardless of which auth method was used.
usersRouter.post("/users/sync", requireAuth, async (req, res) => {
  const uid = res.locals.uid as string;
  const tokenEmail = (res.locals.tokenEmail as string | null) ?? null;
  const tokenName = (res.locals.tokenName as string | null) ?? null;
  const { displayName } = (req.body ?? {}) as SyncUserBody;

  try {
    const profile = await upsertUserProfile(uid, tokenEmail, displayName || tokenName || null);
    res.json(profile);
  } catch (err) {
    res.status(500).json({ error: err instanceof Error ? err.message : "Failed to sync user" });
  }
});
