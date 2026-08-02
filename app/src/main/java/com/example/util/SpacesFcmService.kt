package com.example.util

import com.example.data.auth.AuthRepository
import com.example.data.db.AppDatabase
import com.example.data.repository.SoulRepository
import com.example.data.spaces.SpacesApiClient
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Data-only messages only (see fcmService.ts on the backend) -- this always gets
 * onMessageReceived, even while backgrounded, so the client fully controls whether/how a
 * notification is shown rather than the OS auto-displaying it with no say from the app.
 */
class SpacesFcmService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            try {
                val database = AppDatabase.getDatabase(applicationContext)
                val soulRepository = SoulRepository(userConfigDao = database.userConfigDao())
                val authRepository = AuthRepository()
                val baseUrl = SpacesApiClient.effectiveBaseUrl(soulRepository.getUserConfig().spacesApiBaseUrl)
                if (baseUrl.isBlank()) return@launch
                val idToken = authRepository.getIdToken() ?: return@launch
                SpacesApiClient.registerFcmToken(baseUrl, idToken, token)
            } catch (_: Exception) {
                // Best-effort -- the next app open's registerFcmToken() call will retry anyway.
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data[EXTRA_NOTIFICATION_TYPE] ?: return
        val spaceId = data[EXTRA_SPACE_ID] ?: return
        val personaId = data[EXTRA_PERSONA_ID]
        val groupChatId = data[EXTRA_GROUP_CHAT_ID]
        val title = data["title"] ?: return
        val body = data["body"] ?: return

        val activeKey = when (type) {
            NOTIFICATION_TYPE_DIRECT_CHAT -> personaId?.let { ActiveChatTracker.directChatKey(spaceId, it) }
            NOTIFICATION_TYPE_GROUP_CHAT -> groupChatId?.let { ActiveChatTracker.groupChatKey(spaceId, it) }
            else -> null
        }
        if (activeKey != null && ActiveChatTracker.isActive(activeKey)) {
            // The user is already looking at this exact chat -- they've seen it live via the
            // Firestore listener, a system banner on top would just be noise.
            return
        }

        postSpaceEventNotification(
            context = applicationContext,
            notificationId = Random.nextInt(),
            title = title,
            body = body,
            type = type,
            spaceId = spaceId,
            personaId = personaId,
            groupChatId = groupChatId
        )
    }
}
