# Spaces MCP Server

A remote MCP server + OAuth 2.1 authorization server that lets external AI clients (Claude,
ChatGPT, Grok, or anything else that speaks MCP over Streamable HTTP) read and create data in
your Spaces account -- Spaces, Personas, Places, and group chats. Deliberately **read + create
only**: no delete, no editing existing records, no controlling the live simulation. It talks to
the same Firebase project as the Android app and `backend/`, so anything created here shows up
in the app immediately.

This is a separate service from `backend/` on purpose: `backend/`'s simulation tick loop needs a
continuously running process (it can't run on Vercel's serverless model), while this server is
pure request/response and deploys to Vercel cleanly.

## What's here

- `api/mcp.ts` -- the MCP endpoint (`/mcp`), Streamable HTTP transport, stateless (one
  server+transport pair built fresh per request).
- `api/authorize.ts`, `api/token.ts`, `api/register.ts` -- a minimal OAuth 2.1 authorization
  server (PKCE-mandatory, Dynamic Client Registration, no client secrets).
- `api/well-known/*.ts` -- RFC 8414 / RFC 9728 metadata documents MCP clients use to
  auto-discover the endpoints above.
- `src/oauth/` -- token generation/hashing, PKCE verification, Firestore-backed storage for
  registered clients, auth codes, access tokens, and refresh tokens.
- `src/mcp/tools.ts` -- the 11 tools this server exposes (`list_spaces`, `get_space`,
  `create_space`, `list_personas`, `get_persona`, `create_persona`, `list_places`, `get_place`,
  `create_place`, `list_group_chats`, `create_group_chat`).
- `src/firestoreSchemas.ts` -- the exact document shapes the app itself writes, so anything
  created via MCP is indistinguishable from something created in the app.

## Deploying

1. **Firebase service account** (same as `backend/`'s setup): Firebase Console -> Project
   Settings -> Service Accounts -> Generate new private key, then base64-encode it:
   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("service-account.json"))
   ```
2. **Firebase Web app config** (for the `/authorize` login page's client-side Firebase Auth SDK
   -- these are public identifiers, not secrets): Firebase Console -> Project Settings -> General
   -> "Your apps" -> add a Web app if you don't have one yet (free, no extra setup). Copy
   `apiKey`, `authDomain`, `appId`.
3. **Deploy**:
   ```
   cd mcp-server
   npm install
   vercel deploy --prod
   ```
4. **Set environment variables** (Vercel dashboard -> Project -> Settings -> Environment
   Variables, or `vercel env add <NAME>` per variable): every value listed in `.env.example`.
   `ISSUER_URL` must be your production deployment's exact origin (e.g.
   `https://spaces-mcp.vercel.app`, no trailing slash) -- redeploy after setting it, since the
   OAuth metadata documents read it at request time but the value must be correct from the start
   for clients that cache discovery results.
5. Copy the resulting `https://<project>.vercel.app` URL into the app's Settings -> MCP
   Connection screen.
6. In your AI client (Claude.ai, ChatGPT, Grok, ...), add a remote MCP connector pointing at that
   URL. The client should auto-discover the OAuth endpoints via
   `/.well-known/oauth-authorization-server`, register itself, and prompt you to sign in via the
   `/authorize` login page. This last step -- the actual live OAuth handshake against a real MCP
   client -- can only be verified once deployed; it wasn't (and can't be) tested in the
   environment this server was built in.

## Local development

```
npm install
npm run typecheck
vercel dev
```

`vercel dev` runs the same serverless functions locally against `http://localhost:3000`; set
`ISSUER_URL=http://localhost:3000` in a local `.env` for testing (OAuth metadata self-references
this value).
