import admin from "firebase-admin";

function loadServiceAccount(): admin.ServiceAccount {
  const base64 = process.env.FIREBASE_SERVICE_ACCOUNT_JSON_BASE64;
  if (!base64) {
    throw new Error(
      "Missing FIREBASE_SERVICE_ACCOUNT_JSON_BASE64 -- generate a service account key in " +
        "Firebase Console (Project Settings > Service Accounts) and base64-encode it into the " +
        "Vercel project's environment variables."
    );
  }
  const json = Buffer.from(base64, "base64").toString("utf8");
  return JSON.parse(json) as admin.ServiceAccount;
}

// Vercel reuses warm function instances across invocations, so re-initializing on every call
// would throw ("app already exists") -- guard exactly like a singleton.
function getFirebaseApp(): admin.app.App {
  const existing = admin.apps.find((app) => app?.name === "[DEFAULT]");
  if (existing) return existing;
  return admin.initializeApp({
    credential: admin.credential.cert(loadServiceAccount()),
    projectId: process.env.FIREBASE_PROJECT_ID || undefined,
  });
}

export const firebaseApp = getFirebaseApp();
export const firestore = firebaseApp.firestore();
export const auth = firebaseApp.auth();
export const FieldValue = admin.firestore.FieldValue;
