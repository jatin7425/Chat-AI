package com.example.util

/**
 * Tracks which chat (if any) is currently on screen, so SpacesFcmService can decide whether an
 * incoming push should actually show a banner -- if the user is already looking at the chat a
 * reply just landed in, they've already seen it live via the Firestore listener, and a system
 * notification on top would just be noise. Plain volatile singleton rather than DI: the app has
 * no service-locator/DI framework, and this needs to be readable from FirebaseMessagingService,
 * which Android instantiates outside any ViewModel/Activity scope.
 */
object ActiveChatTracker {
    @Volatile
    var activeKey: String? = null
        private set

    fun setActive(key: String?) {
        activeKey = key
    }

    fun isActive(key: String): Boolean = activeKey == key

    fun directChatKey(spaceId: String, personaId: String) = "direct:$spaceId:$personaId"
    fun groupChatKey(spaceId: String, groupChatId: String) = "group:$spaceId:$groupChatId"
}
