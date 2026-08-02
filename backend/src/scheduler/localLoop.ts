import cron from "node-cron";
import { config } from "../config";
import { tick } from "../orchestrator/tick";

/**
 * Local dev's stand-in for Vercel Cron: runs `tick()` on a schedule in-process. The only file
 * that differs between local and a future Vercel deployment -- there, an `api/cron/tick.ts`
 * function would import and call the same `tick()` on a `vercel.json` crons entry instead.
 */
export function startLocalTickLoop(): void {
  if (!config.runLocalTickLoop) return;

  console.log(`[scheduler] local tick loop starting (${config.tickIntervalCron})`);
  cron.schedule(config.tickIntervalCron, () => {
    tick().catch((err) => console.error("[scheduler] tick failed:", err));
  });
}
