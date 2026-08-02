package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.AuthRepository
import com.example.data.db.AppDatabase
import com.example.data.model.ChatMessageEntity
import com.example.data.model.PersonaEntity
import com.example.data.model.UserConfigEntity
import com.example.data.repository.SoulRepository
import com.example.data.spaces.SpacesApiClient
import com.example.util.NotificationHelper
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class Screen {
    object Auth : Screen()
    object Onboarding : Screen()
    object Personas : Screen()
    data class Chat(val persona: PersonaEntity, val sessionId: String) : Screen()
    data class DualPersonaChat(val personaA: PersonaEntity, val personaB: PersonaEntity, val sessionId: String) : Screen()
    data class MemoryRecap(val persona: PersonaEntity) : Screen()
    object Settings : Screen()
    object LiteLlmServer : Screen()
    object ChatModel : Screen()
    object SpacesBackendSettings : Screen()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val database = AppDatabase.getDatabase(application)
    val soulRepository = SoulRepository(
        personaDao = database.personaDao(),
        userConfigDao = database.userConfigDao(),
        chatDao = database.chatDao(),
        memoryRecapDao = database.memoryRecapDao()
    )

    val personasState: StateFlow<List<PersonaEntity>> = soulRepository.allPersonas
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val userConfigState: StateFlow<UserConfigEntity?> = soulRepository.userConfigFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val authRepository = AuthRepository()
    val currentUser: StateFlow<FirebaseUser?> = authRepository.authStateFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, authRepository.currentUser)

    private val _screenStack = MutableStateFlow(listOf<Screen>(Screen.Auth))
    val currentScreen: StateFlow<Screen> = _screenStack
        .map { it.last() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Screen.Onboarding)
    val canGoBack: StateFlow<Boolean> = _screenStack
        .map { it.size > 1 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun navigateTo(screen: Screen) {
        _screenStack.update { it + screen }
    }

    fun navigateBack(): Boolean {
        val stack = _screenStack.value
        return if (stack.size > 1) {
            _screenStack.value = stack.dropLast(1)
            true
        } else {
            false
        }
    }

    fun resetTo(screen: Screen) {
        _screenStack.value = listOf(screen)
    }

    private var hasRoutedPostAuth = false

    init {
        viewModelScope.launch {
            currentUser.collect { user ->
                if (user == null) {
                    hasRoutedPostAuth = false
                    resetTo(Screen.Auth)
                } else {
                    soulRepository.updateFirebaseIdentity(user.uid, user.email)
                    syncUserWithBackend(user)
                    if (!hasRoutedPostAuth) {
                        hasRoutedPostAuth = true
                        val config = soulRepository.getUserConfig()
                        resetTo(if (config.isOnboardingCompleted) Screen.Personas else Screen.Onboarding)
                    }
                }
            }
        }
    }

    /**
     * Upserts the signed-in user's profile into the backend (Firestore users/{uid}), covering
     * every auth path uniformly: email sign-up, email sign-in, Google SSO, and cold-start
     * rehydration of an already-signed-in session. Uses SpacesApiClient.effectiveBaseUrl, so a
     * manual Settings override wins if set, otherwise the build-injected default (dev-tunnel URL
     * on debug, CI-supplied URL on release) is used automatically -- no manual Settings entry
     * required for the common case. Best-effort and silent on failure: the backend may
     * legitimately be offline (it only runs locally during dev), and this must never block
     * sign-in/sign-up, which succeed or fail purely on Firebase Auth.
     */
    private fun syncUserWithBackend(user: FirebaseUser) {
        viewModelScope.launch {
            val baseUrl = SpacesApiClient.effectiveBaseUrl(soulRepository.getUserConfig().spacesApiBaseUrl)
            if (baseUrl.isBlank()) return@launch
            val idToken = authRepository.getIdToken() ?: return@launch
            SpacesApiClient.syncUser(baseUrl, idToken, user.displayName)
        }
    }

    fun signOut() {
        authRepository.signOut()
        resetTo(Screen.Auth)
    }

    fun completeOnboarding(
        userName: String,
        userBio: String,
        baseUrl: String,
        firstPersona: PersonaEntity
    ) {
        viewModelScope.launch {
            val currentConfig = soulRepository.getUserConfig()
            val updatedConfig = currentConfig.copy(
                userName = userName,
                userBio = userBio,
                baseUrl = baseUrl,
                isOnboardingCompleted = true
            )
            soulRepository.saveUserConfig(updatedConfig)

            // Insert first persona
            val personaId = soulRepository.insertPersona(firstPersona)
            val savedPersona = firstPersona.copy(id = personaId)

            // Navigate to chat with the first persona, clearing Onboarding from the stack
            val session = soulRepository.getOrCreateSessionForPersona(savedPersona)
            resetTo(Screen.Chat(savedPersona, session.sessionId))
        }
    }

    private val _editingPersona = MutableStateFlow<PersonaEntity?>(null)
    val editingPersona: StateFlow<PersonaEntity?> = _editingPersona.asStateFlow()

    private val _showCreateSheet = MutableStateFlow(false)
    val showCreateSheet: StateFlow<Boolean> = _showCreateSheet.asStateFlow()

    fun navigateToPersonas() {
        resetTo(Screen.Personas)
    }

    fun navigateToSettings() {
        navigateTo(Screen.Settings)
    }

    fun navigateToLiteLlmServer() {
        navigateTo(Screen.LiteLlmServer)
    }

    fun navigateToChatModel() {
        navigateTo(Screen.ChatModel)
    }

    fun navigateToSpacesBackendSettings() {
        navigateTo(Screen.SpacesBackendSettings)
    }

    private var isAppInForeground = true

    fun setAppInForeground(inForeground: Boolean) {
        isAppInForeground = inForeground
    }

    fun openChatWithPersona(persona: PersonaEntity) {
        viewModelScope.launch {
            val session = soulRepository.getOrCreateSessionForPersona(persona)
            navigateTo(Screen.Chat(persona, session.sessionId))
        }
    }

    fun openChatByPersonaId(personaId: Long) {
        viewModelScope.launch {
            val persona = soulRepository.getPersonaById(personaId)
            if (persona != null) {
                openChatWithPersona(persona)
            }
        }
    }

    fun sendMessage(
        sessionId: String,
        persona: PersonaEntity,
        userText: String,
        onComplete: ((Result<String>) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val result = soulRepository.sendMessage(sessionId, persona, userText)
            onComplete?.invoke(result)

            if (result.isSuccess) {
                val replyText = result.getOrNull()
                if (!replyText.isNullOrBlank()) {
                    val activeScreen = currentScreen.value
                    val isUserOnThisChat = activeScreen is Screen.Chat && activeScreen.persona.id == persona.id

                    if (!isAppInForeground || !isUserOnThisChat) {
                        NotificationHelper.postPersonaReplyNotification(
                            context = getApplication(),
                            persona = persona,
                            replyText = replyText
                        )
                    }
                }
            }
        }
    }

    fun retryLastMessage(
        sessionId: String,
        persona: PersonaEntity,
        onComplete: ((Result<String>) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val result = soulRepository.retryLastMessage(sessionId, persona)
            onComplete?.invoke(result)
        }
    }

    fun openDualPersonaChat(personaA: PersonaEntity, personaB: PersonaEntity) {
        viewModelScope.launch {
            val session = soulRepository.getOrCreateDualSession(personaA, personaB)
            navigateTo(Screen.DualPersonaChat(personaA, personaB, session.sessionId))
        }
    }

    fun sendDualPersonaTurn(
        personaA: PersonaEntity,
        personaB: PersonaEntity,
        sessionId: String,
        onComplete: ((Result<String>) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val result = soulRepository.sendDualPersonaTurn(personaA, personaB, sessionId)
            onComplete?.invoke(result)
        }
    }

    fun openMemoryRecap(persona: PersonaEntity) {
        navigateTo(Screen.MemoryRecap(persona))
    }

    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>> {
        return soulRepository.getMessagesForSession(sessionId)
    }

    fun openCreatePersonaSheet(personaToEdit: PersonaEntity? = null) {
        _editingPersona.value = personaToEdit
        _showCreateSheet.value = true
    }

    fun closeCreatePersonaSheet() {
        _showCreateSheet.value = false
        _editingPersona.value = null
    }

    fun savePersona(persona: PersonaEntity) {
        viewModelScope.launch {
            if (persona.id == 0L) {
                soulRepository.insertPersona(persona)
            } else {
                soulRepository.updatePersona(persona)
            }
            closeCreatePersonaSheet()
        }
    }

    fun deletePersona(persona: PersonaEntity) {
        viewModelScope.launch {
            soulRepository.deletePersona(persona)
        }
    }
}
