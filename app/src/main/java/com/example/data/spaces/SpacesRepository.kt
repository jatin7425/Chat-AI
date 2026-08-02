package com.example.data.spaces

import android.util.Log
import com.example.data.auth.AuthRepository
import com.example.data.spaces.model.ActivityLogEntryModel
import com.example.data.spaces.model.DirectChatMessageModel
import com.example.data.spaces.model.GroupChatMessageModel
import com.example.data.spaces.model.GroupChatModel
import com.example.data.spaces.model.LlmConfigModel
import com.example.data.spaces.model.NeedsInputModel
import com.example.data.spaces.model.NotificationItemModel
import com.example.data.spaces.model.PlaceModel
import com.example.data.spaces.model.SpaceModel
import com.example.data.spaces.model.SpacePersonaModel
import com.example.data.spaces.model.StoryFeedTaskModel
import com.example.data.spaces.model.UserCharacterModel
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Direct Firestore client SDK access for Spaces/Personas CRUD -- covered entirely by
 * firestore.rules' ownership checks, no backend round-trip needed for these operations (the
 * backend is only involved for space deletion, which needs Admin SDK recursiveDelete, and the
 * photo-appearance-autofill call).
 */
class SpacesRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val authRepository: AuthRepository
) {
    private fun requireUid(): String =
        authRepository.currentUser?.uid ?: throw IllegalStateException("Not signed in")

    private fun usersCollection() = firestore.collection("users")
    private fun spacesCollection() = firestore.collection("spaces")
    private fun personasCollection(spaceId: String) = spacesCollection().document(spaceId).collection("personas")
    private fun userCharacterDoc(spaceId: String) =
        spacesCollection().document(spaceId).collection("userCharacter").document("profile")
    private fun chatMessagesCollection(spaceId: String, personaId: String) =
        spacesCollection().document(spaceId).collection("chats").document(personaId).collection("messages")
    private fun storyFeedCollection(spaceId: String) = spacesCollection().document(spaceId).collection("storyFeed")
    private fun needsInputCollection(spaceId: String) = spacesCollection().document(spaceId).collection("needsInput")
    private fun placesCollection(spaceId: String) = spacesCollection().document(spaceId).collection("places")
    private fun activityLogCollection(spaceId: String, personaId: String) =
        personasCollection(spaceId).document(personaId).collection("activityLog")
    private fun groupChatsCollection(spaceId: String) = spacesCollection().document(spaceId).collection("groupChats")
    private fun groupChatMessagesCollection(spaceId: String, groupChatId: String) =
        groupChatsCollection(spaceId).document(groupChatId).collection("messages")
    private fun notificationsCollection() = firestore.collection("notifications").document(requireUid()).collection("items")

    fun observeSpaces(): Flow<List<SpaceModel>> = callbackFlow {
        val uid = requireUid()
        val query = spacesCollection()
            .whereEqualTo("ownerUid", uid)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                // Firestore's watch stream reconnects periodically and can surface transient
                // errors (network blips, index-still-building, etc.) on an otherwise-healthy
                // listener. Log and keep the last good value on screen rather than wiping it --
                // clearing to empty here was making a correctly-rendered list flicker away.
                Log.w("SpacesRepository", "observeSpaces listener error", error)
                return@addSnapshotListener
            }
            val spaces = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(SpaceModel::class.java)?.copy(id = doc.id)
            } ?: emptyList()
            trySend(spaces)
        }
        awaitClose { registration.remove() }
    }

    suspend fun createSpace(name: String, premise: String): String {
        val uid = requireUid()
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val space = SpaceModel(
            id = id,
            ownerUid = uid,
            name = name,
            premise = premise,
            simDate = "",
            simStatus = "running",
            personaCount = 0,
            createdAt = now,
            updatedAt = now,
            lastTickAt = 0L
        )
        spacesCollection().document(id).set(space).await()
        return id
    }

    suspend fun setSimStatus(spaceId: String, running: Boolean) {
        spacesCollection().document(spaceId).update(
            mapOf(
                "simStatus" to if (running) "running" else "paused",
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()
    }

    fun observePersonas(spaceId: String): Flow<List<SpacePersonaModel>> = callbackFlow {
        val registration = personasCollection(spaceId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("SpacesRepository", "observePersonas listener error", error)
                return@addSnapshotListener
            }
            val personas = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(SpacePersonaModel::class.java)?.copy(id = doc.id)
            } ?: emptyList()
            trySend(personas)
        }
        awaitClose { registration.remove() }
    }

    /** One-shot fetch (not a live listener) -- for resolving a notification-tap deep link into a full model before navigating. */
    suspend fun getSpace(spaceId: String): SpaceModel? {
        val doc = spacesCollection().document(spaceId).get().await()
        return doc.toObject(SpaceModel::class.java)?.copy(id = doc.id)
    }

    /** One-shot fetch (not a live listener) -- for resolving a notification-tap deep link into a full model before navigating. */
    suspend fun getPersona(spaceId: String, personaId: String): SpacePersonaModel? {
        val doc = personasCollection(spaceId).document(personaId).get().await()
        return doc.toObject(SpacePersonaModel::class.java)?.copy(id = doc.id)
    }

    fun observePersona(spaceId: String, personaId: String): Flow<SpacePersonaModel?> = callbackFlow {
        val registration = personasCollection(spaceId).document(personaId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("SpacesRepository", "observePersona listener error", error)
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(snapshot.toObject(SpacePersonaModel::class.java)?.copy(id = snapshot.id))
            }
        awaitClose { registration.remove() }
    }

    suspend fun createPersona(spaceId: String, persona: SpacePersonaModel): String {
        val id = persona.id.ifBlank { UUID.randomUUID().toString() }
        val now = System.currentTimeMillis()
        val toSave = persona.copy(id = id, spaceId = spaceId, createdAt = now, updatedAt = now)
        personasCollection(spaceId).document(id).set(toSave).await()
        spacesCollection().document(spaceId).update(
            mapOf(
                "personaCount" to FieldValue.increment(1),
                "updatedAt" to now
            )
        ).await()
        return id
    }

    suspend fun updatePersona(spaceId: String, personaId: String, persona: SpacePersonaModel) {
        val toSave = persona.copy(
            id = personaId,
            spaceId = spaceId,
            updatedAt = System.currentTimeMillis()
        )
        personasCollection(spaceId).document(personaId).set(toSave).await()
    }

    suspend fun deletePersona(spaceId: String, personaId: String) {
        personasCollection(spaceId).document(personaId).delete().await()
        spacesCollection().document(spaceId).update(
            mapOf(
                "personaCount" to FieldValue.increment(-1),
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()
    }

    fun observeUserCharacter(spaceId: String): Flow<UserCharacterModel?> = callbackFlow {
        val registration = userCharacterDoc(spaceId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("SpacesRepository", "observeUserCharacter listener error", error)
                return@addSnapshotListener
            }
            if (snapshot == null || !snapshot.exists()) {
                trySend(null)
                return@addSnapshotListener
            }
            trySend(snapshot.toObject(UserCharacterModel::class.java))
        }
        awaitClose { registration.remove() }
    }

    suspend fun saveUserCharacter(spaceId: String, character: UserCharacterModel) {
        val toSave = character.copy(updatedAt = System.currentTimeMillis())
        userCharacterDoc(spaceId).set(toSave).await()
    }

    /**
     * The user's LiteLLM connection lives on users/{uid} alongside fields the backend itself
     * owns (email, displayName, fcmTokens, ...). Read/write only these two fields -- never the
     * whole document -- so this repository can't clobber backend-written data.
     */
    fun observeLlmConfig(): Flow<LlmConfigModel> = callbackFlow {
        val uid = requireUid()
        val registration = usersCollection().document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("SpacesRepository", "observeLlmConfig listener error", error)
                return@addSnapshotListener
            }
            trySend(
                LlmConfigModel(
                    llmBaseUrl = snapshot?.getString("llmBaseUrl") ?: "",
                    llmModel = snapshot?.getString("llmModel") ?: ""
                )
            )
        }
        awaitClose { registration.remove() }
    }

    suspend fun saveLlmConfig(baseUrl: String, model: String) {
        val uid = requireUid()
        usersCollection().document(uid).set(
            mapOf("llmBaseUrl" to baseUrl, "llmModel" to model, "updatedAt" to System.currentTimeMillis()),
            SetOptions.merge()
        ).await()
    }

    /** Direct chat with a persona -- client read-only (see firestore.rules); sends go through SpacesApiClient.sendDirectMessage, which the backend writes both sides of. */
    fun observeDirectMessages(spaceId: String, personaId: String): Flow<List<DirectChatMessageModel>> = callbackFlow {
        val registration = chatMessagesCollection(spaceId, personaId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("SpacesRepository", "observeDirectMessages listener error", error)
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(DirectChatMessageModel::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(messages)
            }
        awaitClose { registration.remove() }
    }

    fun observeStoryFeed(spaceId: String): Flow<List<StoryFeedTaskModel>> = callbackFlow {
        val registration = storyFeedCollection(spaceId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("SpacesRepository", "observeStoryFeed listener error", error)
                    return@addSnapshotListener
                }
                val tasks = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(StoryFeedTaskModel::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(tasks)
            }
        awaitClose { registration.remove() }
    }

    fun observeNeedsInput(spaceId: String): Flow<List<NeedsInputModel>> = callbackFlow {
        val registration = needsInputCollection(spaceId)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("SpacesRepository", "observeNeedsInput listener error", error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(NeedsInputModel::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(items)
            }
        awaitClose { registration.remove() }
    }

    fun observePlaces(spaceId: String): Flow<List<PlaceModel>> = callbackFlow {
        val registration = placesCollection(spaceId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("SpacesRepository", "observePlaces listener error", error)
                return@addSnapshotListener
            }
            val places = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(PlaceModel::class.java)?.copy(id = doc.id)
            } ?: emptyList()
            trySend(places)
        }
        awaitClose { registration.remove() }
    }

    suspend fun createPlace(spaceId: String, name: String, description: String): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        placesCollection(spaceId).document(id).set(
            PlaceModel(id = id, name = name, description = description, createdAt = now, updatedAt = now)
        ).await()
        return id
    }

    suspend fun deletePlace(spaceId: String, placeId: String) {
        placesCollection(spaceId).document(placeId).delete().await()
    }

    /** A persona's activity log -- client read-only (see firestore.rules); written by the backend during direct chats and orchestrator tick exchanges. */
    fun observeActivityLog(spaceId: String, personaId: String): Flow<List<ActivityLogEntryModel>> = callbackFlow {
        val registration = activityLogCollection(spaceId, personaId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("SpacesRepository", "observeActivityLog listener error", error)
                    return@addSnapshotListener
                }
                val entries = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ActivityLogEntryModel::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(entries)
            }
        awaitClose { registration.remove() }
    }

    /**
     * A space-wide activity feed across every persona -- a Firestore collectionGroup query over
     * all "activityLog" subcollections, filtered to this space via the denormalized spaceId
     * field. Same listener as any other feed, so it's "live" automatically whenever the
     * simulation is running (new entries stream in) and just shows the saved history otherwise --
     * no separate running/paused mode needed.
     */
    fun observeSpaceActivityLog(spaceId: String, limit: Long = 50): Flow<List<ActivityLogEntryModel>> = callbackFlow {
        val registration = firestore.collectionGroup("activityLog")
            .whereEqualTo("spaceId", spaceId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("SpacesRepository", "observeSpaceActivityLog listener error", error)
                    return@addSnapshotListener
                }
                val entries = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ActivityLogEntryModel::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(entries)
            }
        awaitClose { registration.remove() }
    }

    fun observeGroupChats(spaceId: String): Flow<List<GroupChatModel>> = callbackFlow {
        val registration = groupChatsCollection(spaceId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("SpacesRepository", "observeGroupChats listener error", error)
                return@addSnapshotListener
            }
            val groupChats = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(GroupChatModel::class.java)?.copy(id = doc.id)
            } ?: emptyList()
            trySend(groupChats)
        }
        awaitClose { registration.remove() }
    }

    suspend fun createGroupChat(spaceId: String, name: String, personaIds: List<String>): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        groupChatsCollection(spaceId).document(id).set(
            GroupChatModel(id = id, name = name, personaIds = personaIds, createdAt = now, updatedAt = now)
        ).await()
        return id
    }

    suspend fun deleteGroupChat(spaceId: String, groupChatId: String) {
        groupChatsCollection(spaceId).document(groupChatId).delete().await()
    }

    /** notifications/{uid}/items -- what the orchestrator tick loop logged while the user was away, newest first. */
    fun observeNotifications(): Flow<List<NotificationItemModel>> = callbackFlow {
        val registration = notificationsCollection()
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("SpacesRepository", "observeNotifications listener error", error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(NotificationItemModel::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(items)
            }
        awaitClose { registration.remove() }
    }

    /** firestore.rules only allows toggling the `read` field on a notification item -- everything else is backend-only. */
    suspend fun markNotificationRead(notificationId: String) {
        notificationsCollection().document(notificationId).update("read", true).await()
    }

    /** One-shot fetch (not a live listener) -- for resolving a notification-tap deep link into a full model before navigating. */
    suspend fun getGroupChat(spaceId: String, groupChatId: String): GroupChatModel? {
        val doc = groupChatsCollection(spaceId).document(groupChatId).get().await()
        return doc.toObject(GroupChatModel::class.java)?.copy(id = doc.id)
    }

    /** A group chat's messages -- client read-only (see firestore.rules); the backend appends the user message and every member persona's reply. */
    fun observeGroupChatMessages(spaceId: String, groupChatId: String): Flow<List<GroupChatMessageModel>> = callbackFlow {
        val registration = groupChatMessagesCollection(spaceId, groupChatId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("SpacesRepository", "observeGroupChatMessages listener error", error)
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(GroupChatMessageModel::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(messages)
            }
        awaitClose { registration.remove() }
    }
}
