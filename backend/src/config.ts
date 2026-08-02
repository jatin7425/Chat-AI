import "dotenv/config";

export const config = {
  port: Number(process.env.PORT ?? 8787),

  firebaseServiceAccountJsonBase64: process.env.FIREBASE_SERVICE_ACCOUNT_JSON_BASE64 ?? "",
  firebaseProjectId: process.env.FIREBASE_PROJECT_ID ?? "",

  // Optional shared credential/model overrides. baseUrl/model themselves are per-user (see
  // usersService.getUserLlmConfig) since each user provides their own LiteLLM server -- these
  // only cover cases where every user's proxy happens to share one auth key, or where a
  // dedicated vision-capable model differs from the user's chosen chat model.
  llmApiKey: process.env.LLM_API_KEY ?? "",
  llmVisionModel: process.env.LLM_VISION_MODEL ?? "",

  runLocalTickLoop: (process.env.RUN_LOCAL_TICK_LOOP ?? "false").toLowerCase() === "true",
  tickIntervalCron: process.env.TICK_INTERVAL_CRON ?? "*/30 * * * * *",

  // Cloudflare R2 (S3-compatible) -- persona pfp/chat-background photo storage. Free tier, unlike
  // Firebase Storage which needs the paid Blaze plan.
  r2AccountId: process.env.R2_ACCOUNT_ID ?? "",
  r2AccessKeyId: process.env.R2_ACCESS_KEY_ID ?? "",
  r2SecretAccessKey: process.env.R2_SECRET_ACCESS_KEY ?? "",
  r2BucketName: process.env.R2_BUCKET_NAME ?? "",
  r2PublicBaseUrl: process.env.R2_PUBLIC_BASE_URL ?? "",
};
