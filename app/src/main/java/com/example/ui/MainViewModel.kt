package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.AuthRepository
import com.example.data.db.AppDatabase
import com.example.data.model.UserConfigEntity
import com.example.data.repository.SoulRepository
import com.example.data.spaces.LiteLlmDirectClient
import com.example.data.spaces.SpacesApiClient
import com.example.data.spaces.SpacesRepository
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
import com.example.util.ActiveChatTracker
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class Screen {
    object Auth : Screen()
    object Settings : Screen()
    object SpacesBackendSettings : Screen()
    object McpSettings : Screen()
    object LiteLlmServer : Screen()
    object ChatModel : Screen()
    object SpacesDashboard : Screen()
    data class SpaceHome(val space: SpaceModel) : Screen()
    data class SpacePersonas(val space: SpaceModel) : Screen()
    data class CreateEditSpacePersona(val space: SpaceModel, val personaToEdit: SpacePersonaModel? = null, val prefillName: String = "") : Screen()
    data class EditUserCharacter(val space: SpaceModel) : Screen()
    data class SpaceDirectChat(val space: SpaceModel, val persona: SpacePersonaModel) : Screen()
    data class StoryFeed(val space: SpaceModel) : Screen()
    data class PersonaProfile(val space: SpaceModel, val persona: SpacePersonaModel) : Screen()
    data class PersonaMood(val space: SpaceModel, val persona: SpacePersonaModel) : Screen()
    data class SpacePlaces(val space: SpaceModel) : Screen()
    data class GroupChatsList(val space: SpaceModel) : Screen()
    data class GroupChat(val space: SpaceModel, val groupChat: GroupChatModel) : Screen()
    data class SpaceActivityLog(val space: SpaceModel) : Screen()
    object Notifications : Screen()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val database = AppDatabase.getDatabase(application)
    val soulRepository = SoulRepository(
        userConfigDao = database.userConfigDao()
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

    val spacesRepository = SpacesRepository(authRepository = authRepository)
    val spacesState: StateFlow<List<SpaceModel>> = currentUser
        .flatMapLatest { user -> if (user != null) spacesRepository.observeSpaces() else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _screenStack = MutableStateFlow(listOf<Screen>(Screen.Auth))
    val currentScreen: StateFlow<Screen> = _screenStack
        .map { it.last() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Screen.Auth)
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
                    registerFcmToken()
                    if (!hasRoutedPostAuth) {
                        hasRoutedPostAuth = true
                        resetTo(Screen.SpacesDashboard)
                    }
                }
            }
        }
        // Keeps ActiveChatTracker in sync with whatever chat (if any) is on screen, so a push
        // notification arriving while the user is already looking at that exact chat can be
        // suppressed instead of piling a redundant banner on top of what they're already seeing.
        viewModelScope.launch {
            currentScreen.collect { screen ->
                ActiveChatTracker.setActive(
                    when (screen) {
                        is Screen.SpaceDirectChat -> ActiveChatTracker.directChatKey(screen.space.id, screen.persona.id)
                        is Screen.GroupChat -> ActiveChatTracker.groupChatKey(screen.space.id, screen.groupChat.id)
                        else -> null
                    }
                )
            }
        }
    }

    /** Registers this device's current FCM token with the backend so push notifications can reach it; re-runs on every sign-in since a token can rotate. */
    private fun registerFcmToken() {
        viewModelScope.launch {
            try {
                val baseUrl = SpacesApiClient.effectiveBaseUrl(soulRepository.getUserConfig().spacesApiBaseUrl)
                if (baseUrl.isBlank()) return@launch
                val idToken = authRepository.getIdToken() ?: return@launch
                val fcmToken = FirebaseMessaging.getInstance().token.await()
                SpacesApiClient.registerFcmToken(baseUrl, idToken, fcmToken)
            } catch (_: Exception) {
                // Best-effort -- push registration failing should never block sign-in.
            }
        }
    }

    /** Resolves a notification-tap deep link (space/persona IDs only) into full models and navigates there, rebuilding a sensible back-stack (Dashboard -> Space Home -> chat) since a cold start has no existing stack to append to. */
    fun openSpaceDirectChatById(spaceId: String, personaId: String) {
        viewModelScope.launch {
            val space = spacesRepository.getSpace(spaceId) ?: return@launch
            val persona = spacesRepository.getPersona(spaceId, personaId) ?: return@launch
            resetTo(Screen.SpacesDashboard)
            navigateTo(Screen.SpaceHome(space))
            navigateTo(Screen.SpaceDirectChat(space, persona))
        }
    }

    /** Same as openSpaceDirectChatById but for a group chat deep link. */
    fun openGroupChatById(spaceId: String, groupChatId: String) {
        viewModelScope.launch {
            val space = spacesRepository.getSpace(spaceId) ?: return@launch
            val groupChat = spacesRepository.getGroupChat(spaceId, groupChatId) ?: return@launch
            resetTo(Screen.SpacesDashboard)
            navigateTo(Screen.SpaceHome(space))
            navigateTo(Screen.GroupChatsList(space))
            navigateTo(Screen.GroupChat(space, groupChat))
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

    fun navigateToSettings() {
        navigateTo(Screen.Settings)
    }

    fun navigateToSpacesBackendSettings() {
        navigateTo(Screen.SpacesBackendSettings)
    }

    fun navigateToMcpSettings() {
        navigateTo(Screen.McpSettings)
    }

    fun navigateToLiteLlmServer() {
        navigateTo(Screen.LiteLlmServer)
    }

    fun navigateToChatModel() {
        navigateTo(Screen.ChatModel)
    }

    // The LiteLLM connection the user provides -- persisted to Firestore users/{uid} (not
    // Room) since the backend, not the mobile client, is what calls this server to generate
    // persona replies (Phase 4).
    val llmConfigState: StateFlow<LlmConfigModel> = currentUser
        .flatMapLatest { user -> if (user != null) spacesRepository.observeLlmConfig() else flowOf(LlmConfigModel()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LlmConfigModel())

    fun saveLlmBaseUrl(baseUrl: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            spacesRepository.saveLlmConfig(baseUrl = baseUrl, model = llmConfigState.value.llmModel)
            onDone()
        }
    }

    fun saveLlmModel(model: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            spacesRepository.saveLlmConfig(baseUrl = llmConfigState.value.llmBaseUrl, model = model)
            onDone()
        }
    }

    suspend fun fetchLlmModels(baseUrl: String): Result<List<String>> = LiteLlmDirectClient.fetchModels(baseUrl)

    // Reserved for a future Spaces-notification feature that needs to suppress/allow local
    // notifications depending on whether the app is currently foregrounded.
    private var isAppInForeground = true

    fun setAppInForeground(inForeground: Boolean) {
        isAppInForeground = inForeground
    }

    // --- Spaces ---

    private val _showCreateSpaceSheet = MutableStateFlow(false)
    val showCreateSpaceSheet: StateFlow<Boolean> = _showCreateSpaceSheet.asStateFlow()

    private val _isCreatingSpace = MutableStateFlow(false)
    val isCreatingSpace: StateFlow<Boolean> = _isCreatingSpace.asStateFlow()

    fun openCreateSpaceSheet() {
        _showCreateSpaceSheet.value = true
    }

    fun closeCreateSpaceSheet() {
        _showCreateSpaceSheet.value = false
    }

    fun createSpace(name: String, premise: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            _isCreatingSpace.value = true
            try {
                spacesRepository.createSpace(name, premise)
                _showCreateSpaceSheet.value = false
            } finally {
                _isCreatingSpace.value = false
            }
        }
    }

    fun openSpace(space: SpaceModel) {
        navigateTo(Screen.SpaceHome(space))
        notifySpaceView(space)
    }

    /** Best-effort, fire-and-forget -- lets the backend generate a debounced spontaneous activity beat for idle Spaces (see SpacesApiClient.notifySpaceView). Never blocks navigation or surfaces an error to the user. */
    private fun notifySpaceView(space: SpaceModel) {
        viewModelScope.launch {
            try {
                val baseUrl = SpacesApiClient.effectiveBaseUrl(soulRepository.getUserConfig().spacesApiBaseUrl)
                if (baseUrl.isBlank()) return@launch
                val idToken = authRepository.getIdToken() ?: return@launch
                SpacesApiClient.notifySpaceView(baseUrl, idToken, space.id)
            } catch (_: Exception) {
                // Best-effort -- opening a Space should never fail because of this.
            }
        }
    }

    fun openSpacePersonas(space: SpaceModel) {
        navigateTo(Screen.SpacePersonas(space))
    }

    fun openCreateEditSpacePersona(space: SpaceModel, persona: SpacePersonaModel? = null, prefillName: String = "") {
        navigateTo(Screen.CreateEditSpacePersona(space, persona, prefillName))
    }

    fun openEditUserCharacter(space: SpaceModel) {
        navigateTo(Screen.EditUserCharacter(space))
    }

    fun saveSpacePersona(space: SpaceModel, persona: SpacePersonaModel, isNew: Boolean) {
        viewModelScope.launch {
            if (isNew) {
                spacesRepository.createPersona(space.id, persona)
            } else {
                spacesRepository.updatePersona(space.id, persona.id, persona)
            }
            navigateBack()
        }
    }

    suspend fun uploadPersonaImage(spaceId: String, personaId: String, kind: String, bytes: ByteArray, mimeType: String): Result<String> {
        val baseUrl = SpacesApiClient.effectiveBaseUrl(soulRepository.getUserConfig().spacesApiBaseUrl)
        val idToken = authRepository.getIdToken() ?: return Result.failure(IllegalStateException("Not signed in"))
        return SpacesApiClient.uploadPersonaImage(baseUrl, idToken, spaceId, personaId, kind, bytes, mimeType)
    }

    fun deleteSpacePersona(space: SpaceModel, persona: SpacePersonaModel) {
        viewModelScope.launch {
            spacesRepository.deletePersona(space.id, persona.id)
        }
    }

    fun saveUserCharacter(space: SpaceModel, character: UserCharacterModel) {
        viewModelScope.launch {
            spacesRepository.saveUserCharacter(space.id, character)
            navigateBack()
        }
    }

    fun toggleSimStatus(space: SpaceModel) {
        viewModelScope.launch {
            val startingUp = space.simStatus != "running"
            if (startingUp) {
                // Starting goes through the backend so it can backscan existing chats for
                // unfulfilled commitments and queue them before ticking begins -- a direct
                // Firestore write can't do that.
                val baseUrl = SpacesApiClient.effectiveBaseUrl(soulRepository.getUserConfig().spacesApiBaseUrl)
                val idToken = authRepository.getIdToken()
                if (idToken == null) {
                    _spaceActionError.value = "Not signed in."
                    return@launch
                }
                SpacesApiClient.startSimulation(baseUrl, idToken, space.id)
                    .onFailure { _spaceActionError.value = it.localizedMessage ?: "Couldn't start the simulation." }
            } else {
                spacesRepository.setSimStatus(space.id, running = false)
            }
        }
    }

    /** "Not a persona" on a needs-your-input card -- the extracted name was just a role reference, not a real target; dismisses the card and closes out the matching blocked Story Feed task. */
    fun dismissNeedsInput(space: SpaceModel, requestId: String) {
        viewModelScope.launch {
            val baseUrl = SpacesApiClient.effectiveBaseUrl(soulRepository.getUserConfig().spacesApiBaseUrl)
            val idToken = authRepository.getIdToken()
            if (idToken == null) {
                _spaceActionError.value = "Not signed in."
                return@launch
            }
            SpacesApiClient.dismissNeedsInput(baseUrl, idToken, space.id, requestId)
                .onFailure { _spaceActionError.value = it.localizedMessage ?: "Couldn't dismiss." }
        }
    }

    private val _spaceActionError = MutableStateFlow<String?>(null)
    val spaceActionError: StateFlow<String?> = _spaceActionError.asStateFlow()

    fun clearSpaceActionError() {
        _spaceActionError.value = null
    }

    /** Space deletion goes through the backend (Admin SDK recursiveDelete) -- firestore.rules denies it client-side entirely, since Firestore has no cascade delete for a Space's several subcollections. */
    fun deleteSpace(space: SpaceModel) {
        viewModelScope.launch {
            val baseUrl = SpacesApiClient.effectiveBaseUrl(soulRepository.getUserConfig().spacesApiBaseUrl)
            val idToken = authRepository.getIdToken()
            if (idToken == null) {
                _spaceActionError.value = "Not signed in."
                return@launch
            }
            SpacesApiClient.deleteSpace(baseUrl, idToken, space.id)
                .onFailure { _spaceActionError.value = it.localizedMessage ?: "Couldn't delete that space." }
        }
    }

    fun observePersonas(spaceId: String): Flow<List<SpacePersonaModel>> = spacesRepository.observePersonas(spaceId)

    fun observeUserCharacter(spaceId: String): Flow<UserCharacterModel?> = spacesRepository.observeUserCharacter(spaceId)

    // --- Direct chat / Story Feed (Phase 4) ---

    fun observeDirectMessages(spaceId: String, personaId: String): Flow<List<DirectChatMessageModel>> =
        spacesRepository.observeDirectMessages(spaceId, personaId)

    fun observeStoryFeed(spaceId: String): Flow<List<StoryFeedTaskModel>> = spacesRepository.observeStoryFeed(spaceId)

    fun observeNeedsInput(spaceId: String): Flow<List<NeedsInputModel>> = spacesRepository.observeNeedsInput(spaceId)

    fun openSpaceDirectChat(space: SpaceModel, persona: SpacePersonaModel) {
        clearSendMessageError(persona.id)
        navigateTo(Screen.SpaceDirectChat(space, persona))
    }

    fun openStoryFeed(space: SpaceModel) {
        navigateTo(Screen.StoryFeed(space))
    }

    // Keyed by personaId so sending/retrying in one persona's chat never blocks or shows an
    // error in any other chat's UI -- these used to be single shared flags, which meant sending
    // a message to persona A left persona B's send button looking blocked too.
    private val _sendingPersonaIds = MutableStateFlow<Set<String>>(emptySet())
    val sendingPersonaIds: StateFlow<Set<String>> = _sendingPersonaIds.asStateFlow()

    private val _sendMessageErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val sendMessageErrors: StateFlow<Map<String, String>> = _sendMessageErrors.asStateFlow()

    private fun clearSendMessageError(personaId: String) {
        _sendMessageErrors.update { it - personaId }
    }

    fun sendSpaceDirectMessage(space: SpaceModel, persona: SpacePersonaModel, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _sendingPersonaIds.update { it + persona.id }
            clearSendMessageError(persona.id)
            try {
                val baseUrl = SpacesApiClient.effectiveBaseUrl(soulRepository.getUserConfig().spacesApiBaseUrl)
                val idToken = authRepository.getIdToken()
                if (idToken == null) {
                    _sendMessageErrors.update { it + (persona.id to "Not signed in.") }
                    return@launch
                }
                SpacesApiClient.sendDirectMessage(baseUrl, idToken, space.id, persona.id, text)
                    .onFailure { _sendMessageErrors.update { errs -> errs + (persona.id to (it.localizedMessage ?: "Couldn't send that message.")) } }
            } finally {
                _sendingPersonaIds.update { it - persona.id }
            }
        }
    }

    /** Retries getting the persona's reply to the last message without re-sending it (for when the reply itself failed, not the send). */
    fun retryLastDirectMessage(space: SpaceModel, persona: SpacePersonaModel) {
        viewModelScope.launch {
            _sendingPersonaIds.update { it + persona.id }
            clearSendMessageError(persona.id)
            try {
                val baseUrl = SpacesApiClient.effectiveBaseUrl(soulRepository.getUserConfig().spacesApiBaseUrl)
                val idToken = authRepository.getIdToken()
                if (idToken == null) {
                    _sendMessageErrors.update { it + (persona.id to "Not signed in.") }
                    return@launch
                }
                SpacesApiClient.retryLastMessage(baseUrl, idToken, space.id, persona.id)
                    .onFailure { _sendMessageErrors.update { errs -> errs + (persona.id to (it.localizedMessage ?: "Retry failed.")) } }
            } finally {
                _sendingPersonaIds.update { it - persona.id }
            }
        }
    }

    // --- Persona profile / mood / places ---

    fun openPersonaProfile(space: SpaceModel, persona: SpacePersonaModel) {
        navigateTo(Screen.PersonaProfile(space, persona))
    }

    fun openPersonaMood(space: SpaceModel, persona: SpacePersonaModel) {
        navigateTo(Screen.PersonaMood(space, persona))
    }

    fun openSpacePlaces(space: SpaceModel) {
        navigateTo(Screen.SpacePlaces(space))
    }

    fun openSpaceActivityLog(space: SpaceModel) {
        navigateTo(Screen.SpaceActivityLog(space))
    }

    fun observePlaces(spaceId: String): Flow<List<PlaceModel>> = spacesRepository.observePlaces(spaceId)

    fun observeActivityLog(spaceId: String, personaId: String): Flow<List<ActivityLogEntryModel>> =
        spacesRepository.observeActivityLog(spaceId, personaId)

    fun observeSpaceActivityLog(spaceId: String): Flow<List<ActivityLogEntryModel>> =
        spacesRepository.observeSpaceActivityLog(spaceId)

    // --- Notifications ---

    fun openNotifications() {
        navigateTo(Screen.Notifications)
    }

    val notificationsState: StateFlow<List<NotificationItemModel>> = currentUser
        .flatMapLatest { user -> if (user != null) spacesRepository.observeNotifications() else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadNotificationCount: StateFlow<Int> = notificationsState
        .map { list -> list.count { !it.read } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Marks the item read and deep-links to the persona's direct chat it came from, resolving IDs into full models exactly like a notification-tap deep link does. */
    fun openNotificationItem(item: NotificationItemModel) {
        viewModelScope.launch {
            spacesRepository.markNotificationRead(item.id)
        }
        openSpaceDirectChatById(item.spaceId, item.personaId)
    }

    fun markNotificationRead(item: NotificationItemModel) {
        viewModelScope.launch { spacesRepository.markNotificationRead(item.id) }
    }

    fun createPlace(space: SpaceModel, name: String, description: String) {
        if (name.isBlank()) return
        viewModelScope.launch { spacesRepository.createPlace(space.id, name, description) }
    }

    fun deletePlace(space: SpaceModel, place: PlaceModel) {
        viewModelScope.launch { spacesRepository.deletePlace(space.id, place.id) }
    }

    fun movePersonaToPlace(space: SpaceModel, persona: SpacePersonaModel, placeId: String) {
        viewModelScope.launch {
            val baseUrl = SpacesApiClient.effectiveBaseUrl(soulRepository.getUserConfig().spacesApiBaseUrl)
            val idToken = authRepository.getIdToken() ?: return@launch
            SpacesApiClient.movePersonaToPlace(baseUrl, idToken, space.id, persona.id, placeId)
        }
    }

    // --- Group chats ---

    fun openGroupChatsList(space: SpaceModel) {
        navigateTo(Screen.GroupChatsList(space))
    }

    fun openGroupChat(space: SpaceModel, groupChat: GroupChatModel) {
        clearGroupChatSendError(groupChat.id)
        navigateTo(Screen.GroupChat(space, groupChat))
    }

    fun observeGroupChats(spaceId: String): Flow<List<GroupChatModel>> = spacesRepository.observeGroupChats(spaceId)

    fun observeGroupChatMessages(spaceId: String, groupChatId: String): Flow<List<GroupChatMessageModel>> =
        spacesRepository.observeGroupChatMessages(spaceId, groupChatId)

    fun createGroupChat(space: SpaceModel, name: String, personaIds: List<String>) {
        if (name.isBlank() || personaIds.isEmpty()) return
        viewModelScope.launch { spacesRepository.createGroupChat(space.id, name, personaIds) }
    }

    fun deleteGroupChat(space: SpaceModel, groupChat: GroupChatModel) {
        viewModelScope.launch { spacesRepository.deleteGroupChat(space.id, groupChat.id) }
    }

    // Keyed by groupChatId, same reasoning as the per-persona direct-chat state above.
    private val _sendingGroupChatIds = MutableStateFlow<Set<String>>(emptySet())
    val sendingGroupChatIds: StateFlow<Set<String>> = _sendingGroupChatIds.asStateFlow()

    private val _groupChatSendErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val groupChatSendErrors: StateFlow<Map<String, String>> = _groupChatSendErrors.asStateFlow()

    private fun clearGroupChatSendError(groupChatId: String) {
        _groupChatSendErrors.update { it - groupChatId }
    }

    fun sendGroupChatMessage(space: SpaceModel, groupChat: GroupChatModel, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _sendingGroupChatIds.update { it + groupChat.id }
            clearGroupChatSendError(groupChat.id)
            try {
                val baseUrl = SpacesApiClient.effectiveBaseUrl(soulRepository.getUserConfig().spacesApiBaseUrl)
                val idToken = authRepository.getIdToken()
                if (idToken == null) {
                    _groupChatSendErrors.update { it + (groupChat.id to "Not signed in.") }
                    return@launch
                }
                SpacesApiClient.sendGroupMessage(baseUrl, idToken, space.id, groupChat.id, text)
                    .onFailure { _groupChatSendErrors.update { errs -> errs + (groupChat.id to (it.localizedMessage ?: "Couldn't send that message.")) } }
            } finally {
                _sendingGroupChatIds.update { it - groupChat.id }
            }
        }
    }
}
