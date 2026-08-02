import { chatComplete } from "../llm/llmClient";

export interface PlaceMentionResult {
  mentionsNewPlace: boolean;
  name: string;
  description: string;
}

const NO_PLACE: PlaceMentionResult = { mentionsNewPlace: false, name: "", description: "" };

/**
 * Checks whether a message mentions a real, specific location that isn't already a known Place
 * in this Space -- "let's meet at Cafe Luna" should create a Place named "Cafe Luna", but
 * "let's meet later" or "at the office" (if "Open Office Floor" already exists) should not.
 * Best-effort LLM classification, fired async alongside commitment detection.
 */
export async function detectNewPlaceMention(
  text: string,
  existingPlaceNames: string[],
  llmConfig: { baseUrl: string; model: string }
): Promise<PlaceMentionResult> {
  const systemPrompt =
    "You analyze one message for mentions of a specific physical location/place. Respond with ONLY compact JSON: " +
    '{"mentionsNewPlace": true|false, "name": string, "description": string}. ' +
    `Places already known in this story: ${existingPlaceNames.length > 0 ? existingPlaceNames.join(", ") : "(none yet)"}. ` +
    "Set mentionsNewPlace:true ONLY if the message names a SPECIFIC place (e.g. \"Cafe Luna\", \"the rooftop bar\", " +
    "\"her apartment\") that is NOT already in the known-places list and NOT just a vague reference (\"here\", \"there\", \"somewhere\"). " +
    "name is the place's name as it should be stored (concise, 1-4 words). description is a short (<15 word) description. " +
    "If no new specific place is mentioned, respond {\"mentionsNewPlace\": false, \"name\": \"\", \"description\": \"\"}.";

  try {
    const raw = await chatComplete(
      [
        { role: "system", content: systemPrompt },
        { role: "user", content: text },
      ],
      { baseUrl: llmConfig.baseUrl, model: llmConfig.model, temperature: 0 }
    );

    const jsonMatch = raw.match(/\{[\s\S]*\}/);
    const parsed = JSON.parse(jsonMatch ? jsonMatch[0] : raw);
    if (!parsed.mentionsNewPlace || typeof parsed.name !== "string" || !parsed.name.trim()) {
      return NO_PLACE;
    }
    return {
      mentionsNewPlace: true,
      name: parsed.name.trim(),
      description: typeof parsed.description === "string" ? parsed.description.trim() : "",
    };
  } catch {
    return NO_PLACE;
  }
}
