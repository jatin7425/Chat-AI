import { config } from "../config";

export interface ChatMessage {
  role: "system" | "user" | "assistant";
  content: string;
}

interface ChatCompletionRequestBody {
  model: string;
  messages: ChatMessage[];
  temperature?: number;
  max_tokens?: number;
}

interface ChatCompletionResponseBody {
  id?: string;
  choices?: Array<{
    index?: number;
    message?: { role?: string; content?: string };
    finish_reason?: string;
  }>;
}

function sanitizeBaseUrl(rawUrl: string): string {
  return rawUrl.trim().replace(/\/$/, "");
}

export interface ChatCompleteOptions {
  baseUrl: string;
  model: string;
  apiKey?: string;
  temperature?: number;
  maxTokens?: number;
}

/**
 * Mirrors LiteLlmApi.kt's request/response shape -- an OpenAI-compatible chat-completions call.
 * baseUrl/model are per-request (the user's own LiteLLM server, resolved via
 * usersService.getUserLlmConfig per-caller) rather than a single global config, since this is a
 * multi-user backend and each user provides their own LLM connection.
 */
// If the LLM server hangs mid-response (dead connection, LiteLLM stuck, etc.), a plain fetch()
// with no timeout blocks forever -- and since the tick loop guards against overlapping runs, one
// hung call here doesn't just fail slowly, it permanently freezes the entire simulation until the
// backend process itself is restarted. This is now a safety net; the actual root cause found was
// missing max_tokens (see below) letting some models behind the LiteLLM router generate
// unboundedly (a reasoning model's hidden "thinking" chain, or a provider with no default cap),
// occasionally taking minutes for what should be a one-sentence reply.
const CHAT_COMPLETE_TIMEOUT_MS = 60_000;

// Confirmed via direct testing: the exact same real prompt took 60s+ (timed out) with no
// max_tokens, and 898ms with max_tokens set -- LiteLLM's router sends requests to different
// underlying providers/models under the hood, and at least one of them doesn't apply its own
// sane default cap. This isn't just a length limit though: some models behind the router are
// reasoning models that spend tokens on a hidden "thinking" chain before the visible answer, so a
// cap that's too tight truncates mid-reasoning before the real answer ever appears (confirmed:
// max_tokens=50 cut a reply off inside its own chain-of-thought, and even 800 occasionally ran out
// before the reasoning chain finished, surfacing as "LLM response had no message content"). 1200
// gives more headroom for that while still bounding worst-case generation time -- the actual
// replies this app asks for (1-4 sentences) only ever needed a few dozen completion tokens.
const DEFAULT_MAX_TOKENS = 1200;

export async function chatComplete(messages: ChatMessage[], options: ChatCompleteOptions): Promise<string> {
  const url = `${sanitizeBaseUrl(options.baseUrl)}/v1/chat/completions`;
  const apiKey = options.apiKey ?? config.llmApiKey;

  const body: ChatCompletionRequestBody = {
    model: options.model,
    messages,
    temperature: options.temperature ?? 0.7,
    max_tokens: options.maxTokens ?? DEFAULT_MAX_TOKENS,
  };

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), CHAT_COMPLETE_TIMEOUT_MS);

  let response: Response;
  try {
    response = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...(apiKey ? { Authorization: `Bearer ${apiKey}` } : {}),
      },
      body: JSON.stringify(body),
      signal: controller.signal,
    });
  } catch (err) {
    if (err instanceof Error && err.name === "AbortError") {
      throw new Error(`LLM request timed out after ${CHAT_COMPLETE_TIMEOUT_MS / 1000}s (server may be sleeping or unresponsive)`);
    }
    throw err;
  } finally {
    clearTimeout(timeout);
  }

  if (!response.ok) {
    const text = await response.text().catch(() => "");
    throw new Error(`LLM request failed (${response.status}): ${text || response.statusText}`);
  }

  const data = (await response.json()) as ChatCompletionResponseBody;
  const content = data.choices?.[0]?.message?.content;
  if (!content) {
    throw new Error("LLM response had no message content");
  }
  return content.trim();
}

/**
 * Best-effort "wake up" ping for LiteLLM servers deployed on free tiers (e.g. Render) that spin
 * down after inactivity -- a GET to /v1/models is enough to trigger a cold start without needing
 * a real chat completion. Callers should fire this without awaiting; it swallows its own errors
 * since a sleeping/misconfigured server here should never break anything else.
 */
export async function pingLlmServer(baseUrl: string, apiKey?: string): Promise<void> {
  const url = `${sanitizeBaseUrl(baseUrl)}/v1/models`;
  const key = apiKey ?? config.llmApiKey;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 45_000);
  try {
    await fetch(url, {
      headers: key ? { Authorization: `Bearer ${key}` } : {},
      signal: controller.signal,
    });
  } finally {
    clearTimeout(timeout);
  }
}
