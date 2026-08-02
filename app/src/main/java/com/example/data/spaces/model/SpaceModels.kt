package com.example.data.spaces.model

/**
 * Plain data classes for the spaces/{spaceId} Firestore document tree (see firestore.rules and
 * backend/src/services/spacesService.ts for the schema this mirrors). All-default-arg
 * constructors are required for the Firestore Android SDK's toObject() deserialization.
 */
data class SpaceModel(
    val id: String = "",
    val ownerUid: String = "",
    val name: String = "",
    val premise: String = "",
    val simDate: String = "",
    val simStatus: String = "paused", // "running" | "paused"
    val personaCount: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lastTickAt: Long = 0L
)

data class AppearanceModel(
    val hairColor: String = "",
    val hairStyle: String = "",
    val eyeColor: String = "",
    val skinTone: String = "",
    val build: String = "",
    val height: String = "",
    val extraFeatures: String = ""
)

/**
 * Targeted feelings a persona has toward one specific other persona or the user -- distinct
 * from mood/aggressiveness, which are the persona's general baseline state. All 0..100.
 * Orchestrator-driven: starts at 0 and drifts each tick based on how interactions go (same
 * "drift over time" principle mood already has), not something the user hand-tunes per
 * relationship -- with that many possible pairs per persona, manual sliders don't scale.
 */
data class RelationshipEmotions(
    val affection: Int = 0,
    val love: Int = 0,
    val lust: Int = 0,
    val trust: Int = 0
)

data class SpacePersonaModel(
    val id: String = "",
    val spaceId: String = "",
    val name: String = "",
    val dob: String = "",
    val gender: String = "",
    val relationshipToUser: String = "",
    val bio: String = "",
    val background: String = "",
    val mood: Int = 0, // -100 (hostile/distressed) .. 100 (warm/content)
    val aggressiveness: Int = 0, // 0 (gentle) .. 100 (combative)
    val appearance: AppearanceModel = AppearanceModel(),
    val relationshipsToOtherPersonas: Map<String, String> = emptyMap(),
    val emotionsTowardUser: RelationshipEmotions = RelationshipEmotions(),
    val emotionsTowardPersonas: Map<String, RelationshipEmotions> = emptyMap(),
    val avatarStyle: String = "Avataaars (Modern)",
    val avatarSeed: String = "",
    val avatarImageUrl: String = "",
    val chatBackgroundImageUrl: String = "",
    val chatBackgroundOpacity: Float = 1f,
    val portfolioImageUrls: List<String> = emptyList(),
    val currentPlaceId: String = "",
    val currentPlaceName: String = "",
    /** Durable long-term memory -- significant things (commitments, milestones) that don't scroll out the way the rolling activity-log window does. */
    val coreMemories: List<String> = emptyList(),
    /** Things this persona wants to bring up with the user but hasn't yet, because they already have one unanswered proactive message out -- drained one at a time as the user replies. */
    val pendingTopics: List<String> = emptyList(),
    /** True once this persona has sent an unprompted message the user hasn't replied to yet -- gates further proactive pings in favor of the pendingTopics queue. */
    val awaitingUserReply: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class UserCharacterModel(
    val name: String = "",
    val dob: String = "",
    val background: String = "",
    val appearance: AppearanceModel = AppearanceModel(),
    val currentPlaceId: String = "",
    val currentPlaceName: String = "",
    val updatedAt: Long = 0L
)

/** spaces/{spaceId}/places/{placeId} -- named locations within a Space, user-managed like Personas. */
data class PlaceModel(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/** spaces/{spaceId}/personas/{personaId}/activityLog/{entryId} -- client read-only, backend-written. spaceId/personaId/personaName are denormalized so a Firestore collectionGroup query can power a space-wide feed across every persona. */
data class ActivityLogEntryModel(
    val id: String = "",
    val spaceId: String = "",
    val personaId: String = "",
    val personaName: String = "",
    val type: String = "", // "chat" | "move" | "mood_shift"
    val description: String = "",
    val placeId: String = "",
    val placeName: String = "",
    val createdAt: Long = 0L
)

/**
 * The user-provided LiteLLM connection: a base URL (their own LiteLLM proxy / self-hosted
 * OpenAI-compatible server) and the model to use. Lives on users/{uid} in Firestore -- not
 * Room -- because the backend (not the mobile client) is what actually calls the LLM when
 * generating persona replies, so it needs to be readable server-side per-user.
 */
data class LlmConfigModel(
    val llmBaseUrl: String = "",
    val llmModel: String = ""
)

/** spaces/{spaceId}/chats/{personaId}/messages/{messageId} -- client read-only, backend-written. */
data class DirectChatMessageModel(
    val id: String = "",
    val role: String = "", // "user" | "assistant"
    val text: String = "",
    val createdAt: Long = 0L
)

/** spaces/{spaceId}/storyFeed/{taskId} -- the orchestrator's task list, client read-only. */
data class StoryFeedTaskModel(
    val id: String = "",
    val description: String = "",
    val status: String = "pending", // "pending" | "in_progress" | "blocked" | "done"
    val speakerPersonaId: String = "",
    val targetPersonaId: String? = null,
    val targetPersonaName: String = "",
    val relatedChatPersonaId: String = "",
    val topic: String = "",
    val blockedReason: String? = null,
    val kind: String = "delegate", // "delegate" | "self_followup"
    val priority: Int = 5,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/** notifications/{uid}/items/{notifId} -- client read-only, written by the orchestrator tick loop when a persona follows up/reports back while the user's away. */
data class NotificationItemModel(
    val id: String = "",
    val spaceId: String = "",
    val personaId: String = "",
    val title: String = "",
    val body: String = "",
    val read: Boolean = false,
    val createdAt: Long = 0L
)

/** spaces/{spaceId}/needsInput/{requestId} -- client read-only; resolved automatically by the backend once the named persona exists. */
data class NeedsInputModel(
    val id: String = "",
    val question: String = "",
    val targetPersonaName: String = "",
    val targetChatPersonaId: String = "",
    val status: String = "pending", // "pending" | "resolved"
    val createdAt: Long = 0L
)

/** spaces/{spaceId}/groupChats/{groupChatId} -- a shared thread with multiple personas at once, user-managed like Personas/Places. */
data class GroupChatModel(
    val id: String = "",
    val name: String = "",
    val personaIds: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/** spaces/{spaceId}/groupChats/{groupChatId}/messages/{messageId} -- client read-only, backend-written. */
data class GroupChatMessageModel(
    val id: String = "",
    val role: String = "", // "user" | "assistant"
    val speakerPersonaId: String = "", // blank when role == "user"
    val text: String = "",
    val createdAt: Long = 0L
)
