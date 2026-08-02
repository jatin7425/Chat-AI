import admin from "firebase-admin";
import { config } from "./config";

function loadServiceAccount(): admin.ServiceAccount {
  if (!config.firebaseServiceAccountJsonBase64) {
    throw new Error(
      "Missing FIREBASE_SERVICE_ACCOUNT_JSON_BASE64 -- generate a service account key in " +
        "Firebase Console (Project Settings > Service Accounts) and base64-encode it into .env."
    );
  }
  const json = Buffer.from(config.firebaseServiceAccountJsonBase64, "base64").toString("utf8");
  return JSON.parse(json) as admin.ServiceAccount;
}

export const firebaseApp = admin.initializeApp({
  credential: admin.credential.cert(loadServiceAccount()),
  projectId: config.firebaseProjectId || undefined,
});

export const firestore = firebaseApp.firestore();
export const auth = firebaseApp.auth();
export const messaging = firebaseApp.messaging();
export const FieldValue = admin.firestore.FieldValue;
