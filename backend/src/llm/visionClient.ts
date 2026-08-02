import { requireLlmConfig } from "../config";

export interface AppearanceFields {
  hairColor: string;
  hairStyle: string;
  eyeColor: string;
  skinTone: string;
  build: string;
  height: string;
  extraFeatures: string;
}

const EMPTY_APPEARANCE: AppearanceFields = {
  hairColor: "",
  hairStyle: "",
  eyeColor: "",
  skinTone: "",
  build: "",
  height: "",
  extraFeatures: "",
};

function sanitizeBaseUrl(rawUrl: string): string {
  return rawUrl.trim().replace(/\/$/, "");
}

/** Sends a photo to a vision-capable chat-completions endpoint and asks it to describe appearance as JSON. */
export async function analyzeAppearance(imageBase64: string, mimeType: string): Promise<AppearanceFields> {
  const { baseUrl, apiKey, visionModel } = requireLlmConfig();
  const url = `${sanitizeBaseUrl(baseUrl)}/v1/chat/completions`;

  const prompt =
    "Describe this person's physical appearance. Respond with ONLY a JSON object with these exact " +
    'keys (all string values, use "" for anything you cannot tell): hairColor, hairStyle, eyeColor, ' +
    "skinTone, build, height, extraFeatures (scars, tattoos, accessories, style quirks). No other text.";

  const response = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(apiKey ? { Authorization: `Bearer ${apiKey}` } : {}),
    },
    body: JSON.stringify({
      model: visionModel,
      temperature: 0.2,
      max_tokens: 500,
      messages: [
        {
          role: "user",
          content: [
            { type: "text", text: prompt },
            { type: "image_url", image_url: { url: `data:${mimeType};base64,${imageBase64}` } },
          ],
        },
      ],
    }),
  });

  if (!response.ok) {
    const text = await response.text().catch(() => "");
    throw new Error(`Vision request failed (${response.status}): ${text || response.statusText}`);
  }

  const data = (await response.json()) as { choices?: Array<{ message?: { content?: string } }> };
  const raw = data.choices?.[0]?.message?.content ?? "";

  try {
    const jsonMatch = raw.match(/\{[\s\S]*\}/);
    const parsed = JSON.parse(jsonMatch ? jsonMatch[0] : raw);
    return { ...EMPTY_APPEARANCE, ...parsed };
  } catch {
    return EMPTY_APPEARANCE;
  }
}
