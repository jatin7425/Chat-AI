import "./logger"; // must be first -- patches console.* to prefix every line with a timestamp
import cors from "cors";
import express from "express";
import { healthRouter } from "./routes/health";
import { spacesRouter } from "./routes/spaces";
import { personasRouter } from "./routes/personas";
import { usersRouter } from "./routes/users";
import { messagesRouter } from "./routes/messages";
import { groupMessagesRouter } from "./routes/groupMessages";
import { internalRouter } from "./routes/internal";

/**
 * The Express app itself, with no app.listen() -- shared between the local dev entrypoint
 * (src/index.ts) and the Vercel serverless entrypoint (api/index.ts). Vercel's Node builder
 * invokes this app once per request rather than keeping a persistent process alive, which is
 * exactly why the old node-cron tick loop (scheduler/localLoop.ts, removed) couldn't live here
 * anymore -- the orchestrator is event-driven now (see orchestrator/exchange.ts).
 */
export const app = express();

app.use(cors());
// Default 100kb body limit is too small for base64-encoded photo uploads.
app.use(express.json({ limit: "10mb" }));

app.use("/api", healthRouter);
app.use("/api", spacesRouter);
app.use("/api", personasRouter);
app.use("/api", usersRouter);
app.use("/api", messagesRouter);
app.use("/api", groupMessagesRouter);
app.use("/api", internalRouter);
