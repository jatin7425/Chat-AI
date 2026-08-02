import "./logger"; // must be first -- patches console.* to prefix every line with a timestamp
import cors from "cors";
import express from "express";
import { config } from "./config";
import { healthRouter } from "./routes/health";
import { spacesRouter } from "./routes/spaces";
import { personasRouter } from "./routes/personas";
import { usersRouter } from "./routes/users";
import { messagesRouter } from "./routes/messages";
import { groupMessagesRouter } from "./routes/groupMessages";
import { startLocalTickLoop } from "./scheduler/localLoop";

const app = express();

app.use(cors());
// Default 100kb body limit is too small for base64-encoded photo uploads.
app.use(express.json({ limit: "10mb" }));

app.use("/api", healthRouter);
app.use("/api", spacesRouter);
app.use("/api", personasRouter);
app.use("/api", usersRouter);
app.use("/api", messagesRouter);
app.use("/api", groupMessagesRouter);

app.listen(config.port, () => {
  console.log(`[backend] listening on http://localhost:${config.port}`);
  startLocalTickLoop();
});
