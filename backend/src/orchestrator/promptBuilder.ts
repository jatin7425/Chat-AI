import { ChatMessage } from "../llm/llmClient";
import { RelationshipEmotions } from "../services/chatService";

export interface KnownPersona {
  name: string;
  relationship: string;
}

export interface PersonaPromptContext {
  name: string;
  background: string;
  bio: string;
  appearance: Record<string, string>;
  mood: number;
  aggressiveness: number;
  relationshipToUser: string;
  emotionsTowardUser?: RelationshipEmotions;
  spacePremise: string;
  spaceSimDate: string;
  knownPersonas?: KnownPersona[];
  /** This persona's own memory (their activity-log entries, oldest first) spanning every direct
   *  chat, group chat, and simulated NPC exchange they've been part of -- the same log regardless
   *  of which conversation surface you're building the prompt for, so a persona recalls things
   *  that happened somewhere else entirely. */
  recentMemory?: string[];
  /** Durable long-term memory -- significant things (commitments, relationships formed) that
   *  don't scroll out of recentMemory's fixed window the way ordinary chatter does. */
  coreMemories?: string[];
}

function knownPersonasLine(knownPersonas: KnownPersona[] | undefined): string {
  if (!knownPersonas || knownPersonas.length === 0) return "";
  const parts = knownPersonas.map((p) => (p.relationship ? `${p.name} (${p.relationship})` : p.name));
  return `Other people you already know in this story: ${parts.join(", ")}. If the user mentions one of them, you already know who they are -- don't ask.`;
}

function memoryLine(recentMemory: string[] | undefined): string {
  if (!recentMemory || recentMemory.length === 0) return "";
  return `Recent activity (things that just happened to you, most recent last):\n${recentMemory
    .map((m) => `- ${m}`)
    .join("\n")}`;
}

function coreMemoriesLine(coreMemories: string[] | undefined): string {
  if (!coreMemories || coreMemories.length === 0) return "";
  return `Things you remember long-term, even if they happened a while ago (stay consistent with these -- they really happened to you):\n${coreMemories
    .map((m) => `- ${m}`)
    .join("\n")}`;
}

function emotionsLine(emotions: RelationshipEmotions | undefined, towardWhom: string): string {
  if (!emotions) return "";
  const notable = Object.entries(emotions).filter(([, v]) => v >= 20);
  if (notable.length === 0) return "";
  const parts = notable.map(([k, v]) => `${k} ${v}/100`);
  return `Your feelings toward ${towardWhom}: ${parts.join(", ")}.`;
}

/**
 * Builds the system prompt for a persona's turn in direct chat with the user -- mirrors the old
 * SoulRepository.buildSystemPrompt's shape (identity/relationship/traits/mood) but server-side,
 * now also factoring in the targeted emotions (affection/love/lust/trust) the persona has built
 * up toward the user specifically.
 */
export function buildPersonaSystemPrompt(ctx: PersonaPromptContext): ChatMessage {
  const appearanceLine = Object.entries(ctx.appearance)
    .filter(([, v]) => v)
    .map(([k, v]) => `${k}: ${v}`)
    .join(", ");

  const content = [
    `You are ${ctx.name}, a character in a story titled "${ctx.spacePremise}" (current date: ${ctx.spaceSimDate}).`,
    `Your relationship to the user: ${ctx.relationshipToUser}.`,
    ctx.bio ? `About you: ${ctx.bio}` : "",
    ctx.background ? `Background: ${ctx.background}` : "",
    appearanceLine ? `Appearance: ${appearanceLine}` : "",
    `Current mood: ${ctx.mood} (-100 hostile/distressed to 100 warm/content). Aggressiveness: ${ctx.aggressiveness} (0 gentle to 100 combative). Let these subtly color your tone.`,
    emotionsLine(ctx.emotionsTowardUser, "the user"),
    knownPersonasLine(ctx.knownPersonas),
    coreMemoriesLine(ctx.coreMemories),
    memoryLine(ctx.recentMemory),
    "Stay strictly in character. Keep responses natural and concise (2-4 sentences unless the situation calls for more).",
  ]
    .filter(Boolean)
    .join("\n");

  return { role: "system", content };
}

/**
 * System prompt for a persona's turn in a group chat -- same identity/mood/traits shape as
 * direct chat, but told explicitly who else is in the conversation and to only speak as itself.
 * The actual multi-speaker history is flattened into user/assistant roles by the caller (see
 * routes/groupMessages.ts): this persona's own past lines are "assistant", everyone else's
 * (including the real user) are "user" with a "[Name]: " prefix so the model can tell voices
 * apart without needing per-speaker roles, which chat-completions APIs don't support.
 */
export function buildGroupPersonaSystemPrompt(ctx: PersonaPromptContext, otherParticipantNames: string[]): ChatMessage {
  const appearanceLine = Object.entries(ctx.appearance)
    .filter(([, v]) => v)
    .map(([k, v]) => `${k}: ${v}`)
    .join(", ");

  const content = [
    `You are ${ctx.name}, a character in a story titled "${ctx.spacePremise}" (current date: ${ctx.spaceSimDate}).`,
    `This is a group conversation. Also present: ${otherParticipantNames.join(", ")}, and the user.`,
    `Your relationship to the user: ${ctx.relationshipToUser}.`,
    ctx.bio ? `About you: ${ctx.bio}` : "",
    ctx.background ? `Background: ${ctx.background}` : "",
    appearanceLine ? `Appearance: ${appearanceLine}` : "",
    `Current mood: ${ctx.mood} (-100 hostile/distressed to 100 warm/content). Aggressiveness: ${ctx.aggressiveness} (0 gentle to 100 combative). Let these subtly color your tone.`,
    emotionsLine(ctx.emotionsTowardUser, "the user"),
    coreMemoriesLine(ctx.coreMemories),
    memoryLine(ctx.recentMemory),
    "Messages from other participants are prefixed with their name in brackets, e.g. \"[Name]: ...\" -- do not use that prefix yourself, and do not speak for anyone else.",
    "Stay strictly in character. Keep responses natural and concise (1-3 sentences unless the situation calls for more) -- this is a group chat, not a monologue.",
  ]
    .filter(Boolean)
    .join("\n");

  return { role: "system", content };
}

export interface NpcExchangeContext {
  speaker: PersonaPromptContext;
  target: PersonaPromptContext;
  topic: string;
}

/** System prompt for one side of a simulated NPC<->NPC exchange (the orchestrator tick). */
export function buildNpcTurnSystemPrompt(ctx: NpcExchangeContext, isSpeaker: boolean): ChatMessage {
  const self = isSpeaker ? ctx.speaker : ctx.target;
  const other = isSpeaker ? ctx.target : ctx.speaker;
  const emotionsTowardOther = self.emotionsTowardUser; // caller passes the correct per-target emotions in emotionsTowardUser slot

  const content = [
    `You are ${self.name}, a character in a story titled "${self.spacePremise}" (current date: ${self.spaceSimDate}).`,
    self.bio ? `About you: ${self.bio}` : "",
    self.background ? `Background: ${self.background}` : "",
    `Current mood: ${self.mood} (-100 hostile/distressed to 100 warm/content). Aggressiveness: ${self.aggressiveness} (0 gentle to 100 combative).`,
    emotionsLine(emotionsTowardOther, other.name),
    coreMemoriesLine(self.coreMemories),
    memoryLine(self.recentMemory),
    isSpeaker
      ? `You need to talk to ${other.name} about: ${ctx.topic}. Say this to them naturally, in character, in 1-3 sentences.`
      : `${other.name} is talking to you. Respond naturally, in character, in 1-3 sentences.`,
    "Do not include your own name or a prefix like \"Name:\" before your line.",
  ]
    .filter(Boolean)
    .join("\n");

  return { role: "system", content };
}
