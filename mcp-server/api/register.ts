import type { VercelRequest, VercelResponse } from "@vercel/node";
import { registerClient } from "../src/oauth/store";

interface RegisterRequestBody {
  redirect_uris?: string[];
  client_name?: string;
}

/** Dynamic Client Registration (RFC 7591) -- lets an MCP client self-register the first time a user adds this connector, with no manual "create an OAuth app" step. */
export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (req.method !== "POST") {
    res.status(405).json({ error: "Method not allowed" });
    return;
  }

  const body = (req.body ?? {}) as RegisterRequestBody;
  const redirectUris = body.redirect_uris;
  if (!Array.isArray(redirectUris) || redirectUris.length === 0) {
    res.status(400).json({ error: "invalid_client_metadata", error_description: "redirect_uris is required" });
    return;
  }

  const client = await registerClient(body.client_name ?? "MCP Client", redirectUris);

  res.status(201).json({
    client_id: client.clientId,
    client_name: client.clientName,
    redirect_uris: client.redirectUris,
    token_endpoint_auth_method: "none",
    grant_types: ["authorization_code", "refresh_token"],
    response_types: ["code"],
  });
}
