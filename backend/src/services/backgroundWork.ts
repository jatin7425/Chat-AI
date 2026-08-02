import { waitUntil } from "@vercel/functions";

/**
 * Runs `promiseFactory` after the response is already on its way to the client, without blocking
 * it. On Vercel, a serverless function's process can be frozen the instant the response is sent
 * -- waitUntil() is the platform's mechanism for keeping the invocation alive just long enough
 * for background work (still bounded by the function's max duration) to actually finish. Locally
 * (VERCEL env var absent), `app.listen()` keeps the process running regardless, so this just
 * fires the promise and lets it resolve on its own -- same as the old .then().catch() pattern.
 */
export function runInBackground(promiseFactory: () => Promise<void>): void {
  if (process.env.VERCEL) {
    waitUntil(promiseFactory().catch((err) => console.error("[background] task failed:", err)));
  } else {
    promiseFactory().catch((err) => console.error("[background] task failed:", err));
  }
}
