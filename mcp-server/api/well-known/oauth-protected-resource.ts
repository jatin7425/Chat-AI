import type { VercelRequest, VercelResponse } from "@vercel/node";

/** RFC 9728 protected resource metadata -- tells clients which authorization server(s) can mint tokens accepted at /mcp. */
export default function handler(_req: VercelRequest, res: VercelResponse) {
  const issuer = process.env.ISSUER_URL;
  if (!issuer) {
    res.status(500).json({ error: "Server misconfigured: ISSUER_URL is not set." });
    return;
  }

  res.status(200).json({
    resource: `${issuer}/mcp`,
    authorization_servers: [issuer],
  });
}
