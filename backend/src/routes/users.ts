import { Router } from "express";
import { requireAuth } from "../middleware/authMiddleware";
import { pingLlmServer } from "../llm/llmClient";
import { addFcmToken, getUserLlmConfig, upsertUserProfile } from "../services/usersService";

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

    // Best-effort: wake up the user's LiteLLM server (often a free-tier host like Render that
    // spins down when idle) on every app open, so it's warm by the time they actually chat --
    // never awaited, never allowed to affect the sync response above.
    getUserLlmConfig(uid)
      .then((llmConfig) => pingLlmServer(llmConfig.baseUrl))
      .catch(() => {});
  } catch (err) {
    res.status(500).json({ error: err instanceof Error ? err.message : "Failed to sync user" });
  }
});

interface RegisterFcmTokenBody {
  token?: string;
}

// Called whenever the app gets a fresh FCM registration token (first launch, token rotation,
// app reinstall) -- registers it so push notifications for persona replies/simulation events
// can actually reach this device.
usersRouter.post("/users/fcm-token", requireAuth, async (req, res) => {
  const uid = res.locals.uid as string;
  const { token } = (req.body ?? {}) as RegisterFcmTokenBody;

  if (!token || !token.trim()) {
    res.status(400).json({ error: "token is required" });
    return;
  }

  try {
    await addFcmToken(uid, token.trim());
    res.json({ ok: true });
  } catch (err) {
    res.status(500).json({ error: err instanceof Error ? err.message : "Failed to register token" });
  }
});
