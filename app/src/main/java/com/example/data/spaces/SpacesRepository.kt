package com.example.data.spaces

import com.example.data.auth.AuthRepository
import com.example.data.spaces.model.SpaceModel
import com.example.data.spaces.model.SpacePersonaModel
import com.example.data.spaces.model.UserCharacterModel
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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

    private fun spacesCollection() = firestore.collection("spaces")
    private fun personasCollection(spaceId: String) = spacesCollection().document(spaceId).collection("personas")
    private fun userCharacterDoc(spaceId: String) =
        spacesCollection().document(spaceId).collection("userCharacter").document("profile")

    fun observeSpaces(): Flow<List<SpaceModel>> = callbackFlow {
        val uid = requireUid()
        val query = spacesCollection()
            .whereEqualTo("ownerUid", uid)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
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
                trySend(emptyList())
                return@addSnapshotListener
            }
            val personas = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(SpacePersonaModel::class.java)?.copy(id = doc.id)
            } ?: emptyList()
            trySend(personas)
        }
        awaitClose { registration.remove() }
    }

    fun observePersona(spaceId: String, personaId: String): Flow<SpacePersonaModel?> = callbackFlow {
        val registration = personasCollection(spaceId).document(personaId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(snapshot.toObject(SpacePersonaModel::class.java)?.copy(id = snapshot.id))
            }
        awaitClose { registration.remove() }
    }

    suspend fun createPersona(spaceId: String, persona: SpacePersonaModel): String {
        val id = UUID.randomUUID().toString()
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
            if (error != null || snapshot == null || !snapshot.exists()) {
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
}
