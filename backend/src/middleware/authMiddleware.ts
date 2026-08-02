import { NextFunction, Request, Response } from "express";
import { auth } from "../firebaseAdmin";

/**
 * Verifies the Firebase ID token in `Authorization: Bearer <token>` and stores identity fields
 * on res.locals: uid (always), tokenEmail/tokenName (from the token, may be null -- e.g. a
 * fresh email/password sign-up has no `name` claim; Google sign-in usually does).
 */
export async function requireAuth(req: Request, res: Response, next: NextFunction) {
  const header = req.header("Authorization") ?? "";
  const match = header.match(/^Bearer (.+)$/);
  if (!match) {
    res.status(401).json({ error: "Missing Authorization: Bearer <idToken> header" });
    return;
  }

  try {
    const decoded = await auth.verifyIdToken(match[1]);
    res.locals.uid = decoded.uid;
    res.locals.tokenEmail = decoded.email ?? null;
    res.locals.tokenName = decoded.name ?? null;
    next();
  } catch (err) {
    res.status(401).json({ error: "Invalid or expired ID token" });
  }
}
