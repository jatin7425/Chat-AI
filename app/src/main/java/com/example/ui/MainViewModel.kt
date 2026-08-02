package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.AuthRepository
import com.example.data.db.AppDatabase
import com.example.data.model.UserConfigEntity
import com.example.data.repository.SoulRepository
import com.example.data.spaces.AppearanceFieldsDto
import com.example.data.spaces.LiteLlmDirectClient
import com.example.data.spaces.SpacesApiClient
import com.example.data.spaces.SpacesRepository
import com.example.data.spaces.model.LlmConfigModel
import com.example.data.spaces.model.SpaceModel
import com.example.data.spaces.model.SpacePersonaModel
import com.example.data.spaces.model.UserCharacterModel
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class Screen {
    object Auth : Screen()
    object Settings : Screen()
    object SpacesBackendSettings : Screen()
    object LiteLlmServer : Screen()
    object ChatModel : Screen()
    object SpacesDashboard : Screen()
    data class SpaceHome(val space: SpaceModel) : Screen()
    data class SpacePersonas(val space: SpaceModel) : Screen()
    data class CreateEditSpacePersona(val space: SpaceModel, val personaToEdit: SpacePersonaModel? = null) : Screen()
    data class EditUserCharacter(val space: SpaceModel) : Screen()
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
                    if (!hasRoutedPostAuth) {
                        hasRoutedPostAuth = true
                        resetTo(Screen.SpacesDashboard)
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

    fun navigateToSettings() {
        navigateTo(Screen.Settings)
    }

    fun navigateToSpacesBackendSettings() {
        navigateTo(Screen.SpacesBackendSettings)
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
    }

    fun openSpacePersonas(space: SpaceModel) {
        navigateTo(Screen.SpacePersonas(space))
    }

    fun openCreateEditSpacePersona(space: SpaceModel, persona: SpacePersonaModel? = null) {
        navigateTo(Screen.CreateEditSpacePersona(space, persona))
    }

    fun openEditUserCharacter(space: SpaceModel) {
        navigateTo(Screen.EditUserCharacter(space))
    }

    fun saveSpacePersona(space: SpaceModel, persona: SpacePersonaModel) {
        viewModelScope.launch {
            if (persona.id.isBlank()) {
                spacesRepository.createPersona(space.id, persona)
            } else {
                spacesRepository.updatePersona(space.id, persona.id, persona)
            }
            navigateBack()
        }
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
            spacesRepository.setSimStatus(space.id, running = space.simStatus != "running")
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

    suspend fun analyzePersonaPhoto(space: SpaceModel, imageBase64: String, mimeType: String): Result<AppearanceFieldsDto> {
        val baseUrl = SpacesApiClient.effectiveBaseUrl(soulRepository.getUserConfig().spacesApiBaseUrl)
        val idToken = authRepository.getIdToken()
            ?: return Result.failure(IllegalStateException("Not signed in"))
        return SpacesApiClient.analyzePersonaPhoto(baseUrl, idToken, space.id, imageBase64, mimeType)
    }
}
