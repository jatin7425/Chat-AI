import type { VercelRequest, VercelResponse } from "@vercel/node";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { verifyAccessToken } from "../src/oauth/store";
import { createMcpServer } from "../src/mcp/server";

function unauthorized(res: VercelResponse) {
  const issuer = process.env.ISSUER_URL ?? "";
  res.setHeader("WWW-Authenticate", `Bearer resource_metadata="${issuer}/.well-known/oauth-protected-resource"`);
  res.status(401).json({ error: "unauthorized", error_description: "Missing or invalid access token." });
}

/**
 * Stateless Streamable HTTP endpoint (sessionIdGenerator: undefined) -- a fresh McpServer +
 * transport pair is built for every request rather than reused across calls, which is both the
 * SDK's documented pattern for serverless hosts and what lets `uid` (resolved from this
 * request's bearer token) be safely captured per-request instead of leaking between users on a
 * warm function instance.
 */
export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (req.method !== "POST") {
    res.status(405).json({ error: "Method not allowed -- this server only supports the stateless request/response shape of Streamable HTTP." });
    return;
  }

  const authHeader = req.headers.authorization ?? "";
  const match = authHeader.match(/^Bearer (.+)$/);
  if (!match) {
    unauthorized(res);
    return;
  }

  const tokenInfo = await verifyAccessToken(match[1]);
  if (!tokenInfo) {
    unauthorized(res);
    return;
  }

  const server = createMcpServer(tokenInfo.uid);
  const transport = new StreamableHTTPServerTransport({ sessionIdGenerator: undefined });

  res.on("close", () => {
    transport.close();
    server.close();
  });

  await server.connect(transport);
  await transport.handleRequest(req, res, req.body);
}
