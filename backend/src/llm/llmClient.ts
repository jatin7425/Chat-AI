import { requireLlmConfig } from "../config";

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

/** Mirrors LiteLlmApi.kt's request/response shape -- an OpenAI-compatible chat-completions call. */
export async function chatComplete(
  messages: ChatMessage[],
  options?: { temperature?: number; maxTokens?: number }
): Promise<string> {
  const { baseUrl, apiKey, model } = requireLlmConfig();
  const url = `${sanitizeBaseUrl(baseUrl)}/v1/chat/completions`;

  const body: ChatCompletionRequestBody = {
    model,
    messages,
    temperature: options?.temperature ?? 0.7,
    max_tokens: options?.maxTokens ?? 1000,
  };

  const response = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(apiKey ? { Authorization: `Bearer ${apiKey}` } : {}),
    },
    body: JSON.stringify(body),
  });

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
