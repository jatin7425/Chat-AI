import type { VercelRequest, VercelResponse } from "@vercel/node";
import { verifyPkce } from "../src/oauth/pkce";
import { consumeAuthCode, consumeRefreshToken, createAccessToken, createRefreshToken } from "../src/oauth/store";

interface TokenRequestBody {
  grant_type?: string;
  code?: string;
  redirect_uri?: string;
  client_id?: string;
  code_verifier?: string;
  refresh_token?: string;
}

/**
 * Vercel parses application/json bodies automatically but OAuth token requests are
 * conventionally application/x-www-form-urlencoded -- parse both shapes since MCP clients may
 * send either.
 */
function parseBody(req: VercelRequest): TokenRequestBody {
  if (req.body && typeof req.body === "object") return req.body as TokenRequestBody;
  if (typeof req.body === "string") {
    return Object.fromEntries(new URLSearchParams(req.body)) as TokenRequestBody;
  }
  return {};
}

export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (req.method !== "POST") {
    res.status(405).json({ error: "Method not allowed" });
    return;
  }

  const body = parseBody(req);

  if (body.grant_type === "authorization_code") {
    if (!body.code || !body.redirect_uri || !body.client_id || !body.code_verifier) {
      res.status(400).json({ error: "invalid_request", error_description: "Missing required parameters." });
      return;
    }

    const record = await consumeAuthCode(body.code);
    if (!record) {
      res.status(400).json({ error: "invalid_grant", error_description: "Authorization code is invalid, expired, or already used." });
      return;
    }
    if (record.clientId !== body.client_id || record.redirectUri !== body.redirect_uri) {
      res.status(400).json({ error: "invalid_grant", error_description: "client_id or redirect_uri does not match the original authorization request." });
      return;
    }
    if (!verifyPkce(body.code_verifier, record.codeChallenge)) {
      res.status(400).json({ error: "invalid_grant", error_description: "PKCE verification failed." });
      return;
    }

    const { token: accessToken, expiresAt } = await createAccessToken(record.uid, record.clientId);
    const refreshToken = await createRefreshToken(record.uid, record.clientId);

    res.status(200).json({
      access_token: accessToken,
      token_type: "Bearer",
      expires_in: Math.floor((expiresAt - Date.now()) / 1000),
      refresh_token: refreshToken,
    });
    return;
  }

  if (body.grant_type === "refresh_token") {
    if (!body.refresh_token || !body.client_id) {
      res.status(400).json({ error: "invalid_request", error_description: "Missing required parameters." });
      return;
    }

    const record = await consumeRefreshToken(body.refresh_token);
    if (!record || record.clientId !== body.client_id) {
      res.status(400).json({ error: "invalid_grant", error_description: "Refresh token is invalid, expired, or does not match client_id." });
      return;
    }

    const { token: accessToken, expiresAt } = await createAccessToken(record.uid, record.clientId);
    const refreshToken = await createRefreshToken(record.uid, record.clientId);

    res.status(200).json({
      access_token: accessToken,
      token_type: "Bearer",
      expires_in: Math.floor((expiresAt - Date.now()) / 1000),
      refresh_token: refreshToken,
    });
    return;
  }

  res.status(400).json({ error: "unsupported_grant_type" });
}
