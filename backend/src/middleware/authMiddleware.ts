import { NextFunction, Request, Response } from "express";
import { auth } from "../firebaseAdmin";

/** Verifies the Firebase ID token in `Authorization: Bearer <token>` and stores the uid on `res.locals.uid`. */
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
    next();
  } catch (err) {
    res.status(401).json({ error: "Invalid or expired ID token" });
  }
}
