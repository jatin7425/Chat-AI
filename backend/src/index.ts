import { app } from "./app";
import { config } from "./config";

// Local development only -- the Vercel deployment uses api/index.ts, which imports the same
// `app` but never calls listen() (Vercel invokes it per-request instead of keeping a process alive).
app.listen(config.port, () => {
  console.log(`[backend] listening on http://localhost:${config.port}`);
});
