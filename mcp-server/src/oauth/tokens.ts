import { randomBytes, createHash } from "crypto";

/** Opaque bearer token/authorization code -- not a JWT, so there's no signing key to manage or rotate. */
export function generateOpaqueToken(): string {
  return randomBytes(32).toString("base64url");
}

/** Tokens/codes are stored hashed (never plaintext) so a Firestore read alone can't hand out a working credential. */
export function hashToken(token: string): string {
  return createHash("sha256").update(token).digest("hex");
}
