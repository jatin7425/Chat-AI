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
    val avatarStyle: String = "Avataaars (Modern)",
    val avatarSeed: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class UserCharacterModel(
    val name: String = "",
    val dob: String = "",
    val background: String = "",
    val appearance: AppearanceModel = AppearanceModel(),
    val updatedAt: Long = 0L
)
