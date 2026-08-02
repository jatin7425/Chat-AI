import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { registerTools } from "./tools";

/**
 * Builds a fresh McpServer scoped to a single authenticated user. A new instance is created per
 * HTTP request (see api/mcp.ts) rather than reused across requests -- the SDK's stateless
 * Streamable HTTP pattern for serverless deployments -- so `uid` never leaks between users
 * sharing a warm Vercel function instance.
 */
export function createMcpServer(uid: string): McpServer {
  const server = new McpServer({ name: "spaces-mcp-server", version: "0.1.0" });
  registerTools(server, uid);
  return server;
}
