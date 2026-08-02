import "dotenv/config";

function required(name: string, value: string | undefined): string {
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
}

export const config = {
  port: Number(process.env.PORT ?? 8787),

  firebaseServiceAccountJsonBase64: process.env.FIREBASE_SERVICE_ACCOUNT_JSON_BASE64 ?? "",
  firebaseProjectId: process.env.FIREBASE_PROJECT_ID ?? "",

  llmBaseUrl: process.env.LLM_BASE_URL ?? "",
  llmApiKey: process.env.LLM_API_KEY ?? "",
  llmModel: process.env.LLM_MODEL ?? "",
  llmVisionModel: process.env.LLM_VISION_MODEL ?? "",

  runLocalTickLoop: (process.env.RUN_LOCAL_TICK_LOOP ?? "false").toLowerCase() === "true",
  tickIntervalCron: process.env.TICK_INTERVAL_CRON ?? "*/30 * * * * *",
};

export function requireLlmConfig() {
  return {
    baseUrl: required("LLM_BASE_URL", config.llmBaseUrl),
    apiKey: config.llmApiKey,
    model: required("LLM_MODEL", config.llmModel),
    visionModel: config.llmVisionModel || config.llmModel,
  };
}
