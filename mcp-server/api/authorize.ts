import type { VercelRequest, VercelResponse } from "@vercel/node";
import { auth } from "../src/firebaseAdmin";
import { createAuthCode, getClient } from "../src/oauth/store";

interface AuthorizeQuery {
  response_type?: string;
  client_id?: string;
  redirect_uri?: string;
  code_challenge?: string;
  code_challenge_method?: string;
  state?: string;
}

interface AuthorizePostBody extends AuthorizeQuery {
  id_token?: string;
}

/** Escapes a string for safe embedding inside a JSON.stringify'd blob inside an inline <script> tag. */
function jsonForScript(value: unknown): string {
  return JSON.stringify(value).replace(/</g, "\\u003c");
}

async function validateRequest(params: AuthorizeQuery): Promise<string | null> {
  if (params.response_type !== "code") return "Only response_type=code is supported.";
  if (params.code_challenge_method !== "S256") return "Only code_challenge_method=S256 is supported.";
  if (!params.code_challenge) return "Missing code_challenge.";
  if (!params.client_id) return "Missing client_id.";
  if (!params.redirect_uri) return "Missing redirect_uri.";

  const client = await getClient(params.client_id);
  if (!client) return "Unknown client_id -- register this client first via /register.";
  if (!client.redirectUris.includes(params.redirect_uri)) return "redirect_uri does not match a registered redirect URI for this client.";
  return null;
}

function renderLoginPage(params: AuthorizeQuery): string {
  const firebaseConfig = {
    apiKey: process.env.FIREBASE_WEB_API_KEY,
    authDomain: process.env.FIREBASE_WEB_AUTH_DOMAIN,
    projectId: process.env.FIREBASE_PROJECT_ID,
    appId: process.env.FIREBASE_WEB_APP_ID,
  };

  return `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8" />
  <title>Sign in to Spaces</title>
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <style>
    body { font-family: system-ui, sans-serif; background: #111; color: #eee; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; }
    .card { background: #1b1b1b; border-radius: 16px; padding: 32px; width: 320px; box-shadow: 0 8px 30px rgba(0,0,0,0.4); }
    h1 { font-size: 18px; margin: 0 0 4px; }
    p.sub { color: #999; font-size: 13px; margin: 0 0 20px; }
    input { width: 100%; box-sizing: border-box; padding: 10px 12px; margin-bottom: 10px; border-radius: 10px; border: 1px solid #333; background: #0d0d0d; color: #eee; }
    button { width: 100%; padding: 10px 12px; border-radius: 10px; border: none; margin-top: 4px; font-weight: 600; cursor: pointer; }
    .primary { background: #a3e635; color: #111; }
    .secondary { background: #262626; color: #eee; }
    .error { color: #f87171; font-size: 13px; margin-top: 10px; min-height: 16px; }
  </style>
</head>
<body>
  <div class="card">
    <h1>Sign in to Spaces</h1>
    <p class="sub">Authorizing an AI client to read and create data in your Spaces account.</p>
    <input id="email" type="email" placeholder="Email" autocomplete="username" />
    <input id="password" type="password" placeholder="Password" autocomplete="current-password" />
    <button class="primary" id="signInBtn">Sign in</button>
    <button class="secondary" id="googleBtn">Sign in with Google</button>
    <div class="error" id="errorBox"></div>
  </div>

  <script src="https://www.gstatic.com/firebasejs/10.14.1/firebase-app-compat.js"></script>
  <script src="https://www.gstatic.com/firebasejs/10.14.1/firebase-auth-compat.js"></script>
  <script>
    const oauthParams = ${jsonForScript(params)};
    firebase.initializeApp(${jsonForScript(firebaseConfig)});

    const errorBox = document.getElementById("errorBox");

    async function completeAuthorization(idToken) {
      try {
        const res = await fetch(window.location.pathname, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(Object.assign({}, oauthParams, { id_token: idToken })),
        });
        const data = await res.json();
        if (!res.ok) {
          errorBox.textContent = data.error_description || data.error || "Authorization failed.";
          return;
        }
        window.location.href = data.redirectTo;
      } catch (err) {
        errorBox.textContent = "Something went wrong. Please try again.";
      }
    }

    document.getElementById("signInBtn").addEventListener("click", async () => {
      errorBox.textContent = "";
      const email = document.getElementById("email").value;
      const password = document.getElementById("password").value;
      try {
        const cred = await firebase.auth().signInWithEmailAndPassword(email, password);
        const idToken = await cred.user.getIdToken();
        await completeAuthorization(idToken);
      } catch (err) {
        errorBox.textContent = err.message || "Sign-in failed.";
      }
    });

    document.getElementById("googleBtn").addEventListener("click", async () => {
      errorBox.textContent = "";
      try {
        const provider = new firebase.auth.GoogleAuthProvider();
        const cred = await firebase.auth().signInWithPopup(provider);
        const idToken = await cred.user.getIdToken();
        await completeAuthorization(idToken);
      } catch (err) {
        errorBox.textContent = err.message || "Google sign-in failed.";
      }
    });
  </script>
</body>
</html>`;
}

export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (req.method === "GET") {
    const params = req.query as AuthorizeQuery;
    const validationError = await validateRequest(params);
    if (validationError) {
      res.status(400).send(validationError);
      return;
    }
    res.setHeader("Content-Type", "text/html; charset=utf-8");
    res.status(200).send(renderLoginPage(params));
    return;
  }

  if (req.method === "POST") {
    const body = (req.body ?? {}) as AuthorizePostBody;
    const validationError = await validateRequest(body);
    if (validationError) {
      res.status(400).json({ error: "invalid_request", error_description: validationError });
      return;
    }
    if (!body.id_token) {
      res.status(400).json({ error: "invalid_request", error_description: "Missing id_token." });
      return;
    }

    let uid: string;
    try {
      const decoded = await auth.verifyIdToken(body.id_token);
      uid = decoded.uid;
    } catch {
      res.status(401).json({ error: "invalid_request", error_description: "Invalid or expired Firebase ID token." });
      return;
    }

    const code = await createAuthCode(uid, body.client_id!, body.redirect_uri!, body.code_challenge!);
    const redirectUrl = new URL(body.redirect_uri!);
    redirectUrl.searchParams.set("code", code);
    if (body.state) redirectUrl.searchParams.set("state", body.state);

    res.status(200).json({ redirectTo: redirectUrl.toString() });
    return;
  }

  res.status(405).json({ error: "Method not allowed" });
}
