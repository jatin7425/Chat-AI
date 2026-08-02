import { firestore } from "../firebaseAdmin";
import { generateOpaqueToken, hashToken } from "./tokens";

const AUTH_CODE_TTL_MS = 5 * 60 * 1000; // 5 minutes -- authorization codes are meant to be exchanged immediately
const ACCESS_TOKEN_TTL_MS = 60 * 60 * 1000; // 1 hour
const REFRESH_TOKEN_TTL_MS = 30 * 24 * 60 * 60 * 1000; // 30 days

function clientsCollection() {
  return firestore.collection("mcpClients");
}
function authCodesCollection() {
  return firestore.collection("mcpAuthCodes");
}
function accessTokensCollection() {
  return firestore.collection("mcpAccessTokens");
}
function refreshTokensCollection() {
  return firestore.collection("mcpRefreshTokens");
}

export interface McpClient {
  clientId: string;
  clientName: string;
  redirectUris: string[];
  createdAt: number;
}

/** Dynamic Client Registration (RFC 7591) -- issues a client_id only, no secret, since every client here is a public client relying on PKCE. */
export async function registerClient(clientName: string, redirectUris: string[]): Promise<McpClient> {
  const clientId = generateOpaqueToken();
  const client: McpClient = { clientId, clientName, redirectUris, createdAt: Date.now() };
  await clientsCollection().doc(clientId).set(client);
  return client;
}

export async function getClient(clientId: string): Promise<McpClient | null> {
  const doc = await clientsCollection().doc(clientId).get();
  return doc.exists ? (doc.data() as McpClient) : null;
}

interface AuthCodeRecord {
  uid: string;
  clientId: string;
  redirectUri: string;
  codeChallenge: string;
  expiresAt: number;
  used: boolean;
}

/** Single-use authorization code binding the authenticated Firebase uid to this OAuth grant, with the PKCE challenge attached so /token can verify the matching verifier. */
export async function createAuthCode(
  uid: string,
  clientId: string,
  redirectUri: string,
  codeChallenge: string
): Promise<string> {
  const code = generateOpaqueToken();
  const record: AuthCodeRecord = {
    uid,
    clientId,
    redirectUri,
    codeChallenge,
    expiresAt: Date.now() + AUTH_CODE_TTL_MS,
    used: false,
  };
  await authCodesCollection().doc(hashToken(code)).set(record);
  return code;
}

/** Verifies, single-use-consumes, and returns the auth code's bound data -- or null if invalid/expired/already used. */
export async function consumeAuthCode(code: string): Promise<AuthCodeRecord | null> {
  const ref = authCodesCollection().doc(hashToken(code));
  const doc = await ref.get();
  if (!doc.exists) return null;
  const record = doc.data() as AuthCodeRecord;
  if (record.used || record.expiresAt < Date.now()) return null;
  await ref.update({ used: true });
  return record;
}

interface TokenRecord {
  uid: string;
  clientId: string;
  expiresAt: number;
  createdAt: number;
}

export async function createAccessToken(uid: string, clientId: string): Promise<{ token: string; expiresAt: number }> {
  const token = generateOpaqueToken();
  const expiresAt = Date.now() + ACCESS_TOKEN_TTL_MS;
  const record: TokenRecord = { uid, clientId, expiresAt, createdAt: Date.now() };
  await accessTokensCollection().doc(hashToken(token)).set(record);
  return { token, expiresAt };
}

export async function verifyAccessToken(token: string): Promise<{ uid: string; clientId: string } | null> {
  const doc = await accessTokensCollection().doc(hashToken(token)).get();
  if (!doc.exists) return null;
  const record = doc.data() as TokenRecord;
  if (record.expiresAt < Date.now()) return null;
  return { uid: record.uid, clientId: record.clientId };
}

export async function createRefreshToken(uid: string, clientId: string): Promise<string> {
  const token = generateOpaqueToken();
  const record: TokenRecord = { uid, clientId, expiresAt: Date.now() + REFRESH_TOKEN_TTL_MS, createdAt: Date.now() };
  await refreshTokensCollection().doc(hashToken(token)).set(record);
  return token;
}

/** Verifies and deletes the refresh token (rotation -- the caller must issue a fresh one alongside a new access token). */
export async function consumeRefreshToken(token: string): Promise<{ uid: string; clientId: string } | null> {
  const ref = refreshTokensCollection().doc(hashToken(token));
  const doc = await ref.get();
  if (!doc.exists) return null;
  const record = doc.data() as TokenRecord;
  await ref.delete();
  if (record.expiresAt < Date.now()) return null;
  return { uid: record.uid, clientId: record.clientId };
}
