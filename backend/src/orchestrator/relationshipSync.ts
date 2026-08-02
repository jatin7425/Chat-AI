import { chatComplete } from "../llm/llmClient";
import { getAllPersonaDocs, updatePersonaRelationship, PersonaDoc, RelationshipEmotions } from "../services/chatService";

interface RelationshipPair {
  aToB: string;
  bToA: string;
}

const EMPTY_EMOTIONS: RelationshipEmotions = { affection: 0, love: 0, lust: 0, trust: 0 };

function emotionsLine(name: string, emotions: RelationshipEmotions): string {
  return `Feelings toward ${name}: affection ${emotions.affection}/100, love ${emotions.love}/100, lust ${emotions.lust}/100, trust ${emotions.trust}/100.`;
}

/**
 * One combined LLM call infers both directions of a pair's relationship at once (same
 * one-call-covers-both-things pattern as commitmentDetector.ts) -- cheaper than two separate
 * calls, and lets the model naturally account for asymmetry (A may see B very differently than
 * B sees A).
 */
async function inferRelationshipPair(
  personaA: PersonaDoc,
  personaB: PersonaDoc,
  llmConfig: { baseUrl: string; model: string }
): Promise<RelationshipPair> {
  const emotionsAtoB = personaA.emotionsTowardPersonas[personaB.id] ?? EMPTY_EMOTIONS;
  const emotionsBtoA = personaB.emotionsTowardPersonas[personaA.id] ?? EMPTY_EMOTIONS;
  const existingAtoB = personaA.relationshipsToOtherPersonas[personaB.id] ?? "";
  const existingBtoA = personaB.relationshipsToOtherPersonas[personaA.id] ?? "";

  const systemPrompt =
    "You update how two characters in a roleplay story currently see each other, based on their measured " +
    'feelings toward one another. Respond with ONLY compact JSON: {"aToB": string, "bToA": string}. ' +
    "Each value is a short (3-10 word) natural relationship description from that character's own point of view " +
    '(e.g. "close friend, fully trusts them", "professional rival, tense", "secret crush, nervous around them"). ' +
    "If an existing description is given, evolve it rather than discarding it -- keep continuity unless the " +
    "feelings clearly suggest a shift. The two directions can differ -- relationships aren't always mutual.";

  const userPrompt = [
    `${personaA.name}${personaA.background ? ` (${personaA.background})` : ""}:`,
    `- ${emotionsLine(personaB.name, emotionsAtoB)}`,
    `- Existing relationship description of ${personaB.name}: ${existingAtoB ? `"${existingAtoB}"` : "none yet"}`,
    "",
    `${personaB.name}${personaB.background ? ` (${personaB.background})` : ""}:`,
    `- ${emotionsLine(personaA.name, emotionsBtoA)}`,
    `- Existing relationship description of ${personaA.name}: ${existingBtoA ? `"${existingBtoA}"` : "none yet"}`,
  ].join("\n");

  try {
    const raw = await chatComplete(
      [
        { role: "system", content: systemPrompt },
        { role: "user", content: userPrompt },
      ],
      { baseUrl: llmConfig.baseUrl, model: llmConfig.model, temperature: 0.3 }
    );

    const jsonMatch = raw.match(/\{[\s\S]*\}/);
    const parsed = JSON.parse(jsonMatch ? jsonMatch[0] : raw);
    return {
      aToB: typeof parsed.aToB === "string" && parsed.aToB.trim() ? parsed.aToB.trim() : existingAtoB,
      bToA: typeof parsed.bToA === "string" && parsed.bToA.trim() ? parsed.bToA.trim() : existingBtoA,
    };
  } catch {
    // Best-effort -- leave the existing labels untouched rather than wiping them on a parse failure.
    return { aToB: existingAtoB, bToA: existingBtoA };
  }
}

/**
 * Refreshes every persona-to-persona relationship label in a Space based on how they currently
 * feel about each other (emotionsTowardPersonas, driven by mood/emotion drift from every direct
 * chat, group chat, and persona-to-persona exchange) -- one combined call per unordered pair.
 */
export async function syncSpaceRelationships(
  spaceId: string,
  llmConfig: { baseUrl: string; model: string }
): Promise<{ pairsUpdated: number }> {
  const personas = await getAllPersonaDocs(spaceId);
  let pairsUpdated = 0;

  for (let i = 0; i < personas.length; i++) {
    for (let j = i + 1; j < personas.length; j++) {
      const personaA = personas[i];
      const personaB = personas[j];
      try {
        const { aToB, bToA } = await inferRelationshipPair(personaA, personaB, llmConfig);
        await Promise.all([
          updatePersonaRelationship(spaceId, personaA.id, personaB.id, aToB),
          updatePersonaRelationship(spaceId, personaB.id, personaA.id, bToA),
        ]);
        pairsUpdated++;
      } catch (err) {
        console.error(`[relationshipSync] failed for pair ${personaA.id}/${personaB.id} in space ${spaceId}:`, err);
      }
    }
  }

  return { pairsUpdated };
}
