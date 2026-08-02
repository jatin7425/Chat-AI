/**
 * Single source of truth for the Firestore document shapes an external MCP client is allowed to
 * create. Deliberately mirrors the mobile app's write shapes field-for-field (see
 * app/src/main/java/com/example/data/spaces/model/SpaceModels.kt and
 * app/src/main/java/com/example/data/spaces/SpacesRepository.kt) so anything created here shows
 * up in the app exactly as if the user had created it themselves, and vice versa.
 */

export interface AppearanceInput {
  hairColor?: string;
  hairStyle?: string;
  eyeColor?: string;
  skinTone?: string;
  build?: string;
  height?: string;
  extraFeatures?: string;
}

export function buildSpaceDoc(id: string, ownerUid: string, name: string, premise: string) {
  const now = Date.now();
  return {
    id,
    ownerUid,
    name,
    premise,
    simDate: "",
    // An externally-created Space starts paused -- an AI client populating a Space shouldn't
    // silently kick off the live simulation loop against the user's configured LLM.
    simStatus: "paused" as const,
    personaCount: 0,
    createdAt: now,
    updatedAt: now,
    lastTickAt: 0,
  };
}

export function buildPersonaDoc(
  id: string,
  name: string,
  opts: {
    dob?: string;
    gender?: string;
    relationshipToUser?: string;
    bio?: string;
    background?: string;
    appearance?: AppearanceInput;
  }
) {
  const now = Date.now();
  return {
    id,
    name,
    dob: opts.dob ?? "",
    gender: opts.gender ?? "",
    relationshipToUser: opts.relationshipToUser ?? "",
    bio: opts.bio ?? "",
    background: opts.background ?? "",
    mood: 0,
    aggressiveness: 0,
    appearance: {
      hairColor: opts.appearance?.hairColor ?? "",
      hairStyle: opts.appearance?.hairStyle ?? "",
      eyeColor: opts.appearance?.eyeColor ?? "",
      skinTone: opts.appearance?.skinTone ?? "",
      build: opts.appearance?.build ?? "",
      height: opts.appearance?.height ?? "",
      extraFeatures: opts.appearance?.extraFeatures ?? "",
    },
    relationshipsToOtherPersonas: {},
    emotionsTowardUser: { affection: 0, love: 0, lust: 0, trust: 0 },
    emotionsTowardPersonas: {},
    avatarStyle: "",
    avatarSeed: name,
    avatarImageUrl: "",
    chatBackgroundImageUrl: "",
    chatBackgroundOpacity: 1,
    portfolioImageUrls: [] as string[],
    currentPlaceId: "",
    currentPlaceName: "",
    coreMemories: [] as string[],
    pendingTopics: [] as string[],
    awaitingUserReply: false,
    createdAt: now,
    updatedAt: now,
  };
}

export function buildPlaceDoc(id: string, name: string, description: string) {
  const now = Date.now();
  return { id, name, description, createdAt: now, updatedAt: now };
}

export function buildGroupChatDoc(id: string, name: string, personaIds: string[]) {
  const now = Date.now();
  return { id, name, personaIds, createdAt: now, updatedAt: now };
}
