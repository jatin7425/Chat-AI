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

  // Event-driven orchestrator (see orchestrator/exchange.ts) -- a bounded persona-to-persona
  // exchange advances itself across separate serverless invocations by calling this same
  // deployment's own /api/internal/advance-exchange endpoint. internalBaseUrl is this
  // deployment's own origin (e.g. https://your-backend.vercel.app, or http://localhost:PORT for
  // local dev); internalTaskSecret gates that endpoint since it's server-to-server only.
  internalBaseUrl: process.env.INTERNAL_BASE_URL ?? `http://localhost:${Number(process.env.PORT ?? 8787)}`,
  internalTaskSecret: process.env.INTERNAL_TASK_SECRET ?? "",

  // Cloudflare R2 (S3-compatible) -- persona pfp/chat-background photo storage. Free tier, unlike
  // Firebase Storage which needs the paid Blaze plan.
  r2AccountId: process.env.R2_ACCOUNT_ID ?? "",
  r2AccessKeyId: process.env.R2_ACCESS_KEY_ID ?? "",
  r2SecretAccessKey: process.env.R2_SECRET_ACCESS_KEY ?? "",
  r2BucketName: process.env.R2_BUCKET_NAME ?? "",
  r2PublicBaseUrl: process.env.R2_PUBLIC_BASE_URL ?? "",
};
