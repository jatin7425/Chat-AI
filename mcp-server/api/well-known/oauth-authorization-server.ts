import type { VercelRequest, VercelResponse } from "@vercel/node";

/** RFC 8414 authorization server metadata -- how MCP clients discover our OAuth endpoints without any manual configuration. */
export default function handler(_req: VercelRequest, res: VercelResponse) {
  const issuer = process.env.ISSUER_URL;
  if (!issuer) {
    res.status(500).json({ error: "Server misconfigured: ISSUER_URL is not set." });
    return;
  }

  res.status(200).json({
    issuer,
    authorization_endpoint: `${issuer}/authorize`,
    token_endpoint: `${issuer}/token`,
    registration_endpoint: `${issuer}/register`,
    scopes_supported: ["spaces"],
    response_types_supported: ["code"],
    grant_types_supported: ["authorization_code", "refresh_token"],
    token_endpoint_auth_methods_supported: ["none"],
    code_challenge_methods_supported: ["S256"],
  });
}
