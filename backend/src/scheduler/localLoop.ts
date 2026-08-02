import cron from "node-cron";
import { config } from "../config";
import { tick } from "../orchestrator/tick";

/**
 * Local dev's stand-in for Vercel Cron: runs `tick()` on a schedule in-process. The only file
 * that differs between local and a future Vercel deployment -- there, an `api/cron/tick.ts`
 * function would import and call the same `tick()` on a `vercel.json` crons entry instead.
 *
 * Guards against overlapping runs: ticks now process every due task per Space (not just one) and
 * can involve many LLM calls, so a slow tick easily outlasts the schedule interval -- without
 * this guard, node-cron would just fire the next tick on top of a still-running one, risking the
 * same task being picked up and processed twice concurrently.
 */
export function startLocalTickLoop(): void {
  if (!config.runLocalTickLoop) return;

  console.log(`[scheduler] local tick loop starting (${config.tickIntervalCron})`);
  let running = false;
  cron.schedule(config.tickIntervalCron, () => {
    if (running) {
      console.log("[scheduler] previous tick still running, skipping this cycle");
      return;
    }
    running = true;
    tick()
      .catch((err) => console.error("[scheduler] tick failed:", err))
      .finally(() => {
        running = false;
      });
  });
}
