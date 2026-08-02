import { ChatMessage } from "../llm/llmClient";

export interface PersonaPromptContext {
  name: string;
  background: string;
  appearance: Record<string, string>;
  mood: number;
  aggressiveness: number;
  relationshipToUser: string;
  spacePremise: string;
  spaceSimDate: string;
}

/**
 * Builds the system prompt for a persona's turn -- mirrors SoulRepository.buildSystemPrompt's
 * shape (identity/relationship/traits/mood, then core directives) but server-side. Fleshed out
 * in Phase 4 alongside the direct-chat and NPC<->NPC message routes that consume it.
 */
export function buildPersonaSystemPrompt(ctx: PersonaPromptContext): ChatMessage {
  const appearanceLine = Object.entries(ctx.appearance)
    .filter(([, v]) => v)
    .map(([k, v]) => `${k}: ${v}`)
    .join(", ");

  const content = [
    `You are ${ctx.name}, a character in a story titled "${ctx.spacePremise}" (current date: ${ctx.spaceSimDate}).`,
    `Your relationship to the user: ${ctx.relationshipToUser}.`,
    ctx.background ? `Background: ${ctx.background}` : "",
    appearanceLine ? `Appearance: ${appearanceLine}` : "",
    `Current mood: ${ctx.mood} (-100 hostile/distressed to 100 warm/content). Aggressiveness: ${ctx.aggressiveness} (0 gentle to 100 combative). Let these subtly color your tone.`,
    "Stay strictly in character. Keep responses natural and concise.",
  ]
    .filter(Boolean)
    .join("\n");

  return { role: "system", content };
}
