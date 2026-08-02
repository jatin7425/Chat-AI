import { createHash } from "crypto";

/**
 * OAuth 2.1 mandates PKCE for public clients (no client secret, since Dynamic Client
 * Registration never issues one here). Only the S256 challenge method is supported -- "plain" is
 * intentionally not accepted, matching the stricter subset of the spec MCP clients are expected
 * to use.
 */
export function verifyPkce(codeVerifier: string, codeChallenge: string): boolean {
  const computed = createHash("sha256").update(codeVerifier).digest("base64url");
  return computed === codeChallenge;
}
