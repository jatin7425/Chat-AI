package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel
import com.example.ui.Screen
import com.example.ui.auth.AuthScreen
import com.example.ui.settings.ChatModelScreen
import com.example.ui.settings.LiteLlmServerScreen
import com.example.ui.settings.McpSettingsScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SpacesBackendSettingsScreen
import com.example.ui.spaces.chat.SpaceDirectChatScreen
import com.example.ui.spaces.dashboard.CreateSpaceSheet
import com.example.ui.spaces.dashboard.SpacesDashboardScreen
import com.example.ui.spaces.groupchat.GroupChatScreen
import com.example.ui.spaces.groupchat.GroupChatsListScreen
import com.example.ui.spaces.notifications.NotificationsScreen
import com.example.ui.spaces.home.SpaceActivityLogScreen
import com.example.ui.spaces.home.SpaceHomeScreen
import com.example.ui.spaces.personas.CreateEditSpacePersonaScreen
import com.example.ui.spaces.personas.SpacePersonaMoodScreen
import com.example.ui.spaces.personas.SpacePersonaProfileScreen
import com.example.ui.spaces.personas.SpacePersonasScreen
import com.example.ui.spaces.places.SpacePlacesScreen
import com.example.ui.spaces.storyfeed.StoryFeedScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.EXTRA_GROUP_CHAT_ID
import com.example.util.EXTRA_NOTIFICATION_TYPE
import com.example.util.EXTRA_PERSONA_ID
import com.example.util.EXTRA_SPACE_ID
import com.example.util.NOTIFICATION_TYPE_DIRECT_CHAT
import com.example.util.NOTIFICATION_TYPE_GROUP_CHAT

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Requested up front even though nothing posts a notification yet -- Spaces
        // notifications (Phase 5) will need this permission on API 33+.
        requestNotificationPermission()
        handleNotificationIntent(intent)

        setContent {
            val userConfig by viewModel.userConfigState.collectAsStateWithLifecycle()

            MyApplicationTheme(darkTheme = userConfig?.darkTheme ?: true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SoulAppContent(viewModel = viewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.setAppInForeground(true)
    }

    override fun onStop() {
        super.onStop()
        viewModel.setAppInForeground(false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    /** Resolves a tapped notification's deep-link extras (see NotificationHelper.kt) into a navigation call, whether the app was already running (onNewIntent, singleTop) or is cold-starting (onCreate). */
    private fun handleNotificationIntent(intent: Intent) {
        val type = intent.getStringExtra(EXTRA_NOTIFICATION_TYPE) ?: return
        val spaceId = intent.getStringExtra(EXTRA_SPACE_ID) ?: return
        when (type) {
            NOTIFICATION_TYPE_DIRECT_CHAT -> {
                val personaId = intent.getStringExtra(EXTRA_PERSONA_ID) ?: return
                viewModel.openSpaceDirectChatById(spaceId, personaId)
            }
            NOTIFICATION_TYPE_GROUP_CHAT -> {
                val groupChatId = intent.getStringExtra(EXTRA_GROUP_CHAT_ID) ?: return
                viewModel.openGroupChatById(spaceId, groupChatId)
            }
        }
        intent.removeExtra(EXTRA_NOTIFICATION_TYPE)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
fun SoulAppContent(viewModel: MainViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val canGoBack by viewModel.canGoBack.collectAsStateWithLifecycle()
    val userConfig by viewModel.userConfigState.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val spaces by viewModel.spacesState.collectAsStateWithLifecycle()
    val showCreateSpaceSheet by viewModel.showCreateSpaceSheet.collectAsStateWithLifecycle()
    val isCreatingSpace by viewModel.isCreatingSpace.collectAsStateWithLifecycle()
    val spaceActionError by viewModel.spaceActionError.collectAsStateWithLifecycle()
    val llmConfig by viewModel.llmConfigState.collectAsStateWithLifecycle()

    androidx.activity.compose.BackHandler(enabled = canGoBack) {
        viewModel.navigateBack()
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
             scaleIn(initialScale = 0.96f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
            .togetherWith(
                fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                scaleOut(targetScale = 1.02f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            )
        },
        label = "screen_transition"
    ) { screen ->
        when (screen) {
            is Screen.Auth -> {
                AuthScreen(
                    authRepository = viewModel.authRepository,
                    onAuthenticated = { /* no-op: MainViewModel's currentUser collector drives routing */ }
                )
            }
            is Screen.SpacesDashboard -> {
                val unreadNotificationCount by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()
                SpacesDashboardScreen(
                    spaces = spaces,
                    onOpenSpace = { space -> viewModel.openSpace(space) },
                    onCreateSpace = { viewModel.openCreateSpaceSheet() },
                    onOpenSettings = { viewModel.navigateToSettings() },
                    onDeleteSpace = { space -> viewModel.deleteSpace(space) },
                    deleteError = spaceActionError,
                    onDismissDeleteError = { viewModel.clearSpaceActionError() },
                    onOpenNotifications = { viewModel.openNotifications() },
                    unreadNotificationCount = unreadNotificationCount
                )
            }
            is Screen.Notifications -> {
                val notifications by viewModel.notificationsState.collectAsStateWithLifecycle()
                NotificationsScreen(
                    notifications = notifications,
                    onBack = { viewModel.navigateBack() },
                    onOpenNotification = { item -> viewModel.openNotificationItem(item) }
                )
            }
            is Screen.SpaceHome -> {
                val personas by viewModel.observePersonas(screen.space.id).collectAsStateWithLifecycle(initialValue = emptyList())
                val userCharacter by viewModel.observeUserCharacter(screen.space.id).collectAsStateWithLifecycle(initialValue = null)
                // screen.space is a frozen snapshot captured at navigation time -- toggling sim
                // status writes to Firestore fine, but without re-deriving from the live list
                // here, this screen's own "Live/Paused" pill would never visually update until
                // the user left and re-entered the space.
                val liveSpace = spaces.find { it.id == screen.space.id } ?: screen.space

                SpaceHomeScreen(
                    space = liveSpace,
                    userCharacter = userCharacter,
                    personas = personas,
                    onBack = { viewModel.navigateBack() },
                    onToggleSim = { viewModel.toggleSimStatus(liveSpace) },
                    onEditUserCharacter = { viewModel.openEditUserCharacter(liveSpace) },
                    onOpenPersonas = { viewModel.openSpacePersonas(liveSpace) },
                    onOpenPersonaChat = { persona -> viewModel.openSpaceDirectChat(liveSpace, persona) },
                    onOpenPlaces = { viewModel.openSpacePlaces(liveSpace) },
                    onOpenGroupChats = { viewModel.openGroupChatsList(liveSpace) },
                    onOpenActivityLog = { viewModel.openSpaceActivityLog(liveSpace) }
                )
            }
            is Screen.SpacePersonas -> {
                val personas by viewModel.observePersonas(screen.space.id).collectAsStateWithLifecycle(initialValue = emptyList())

                SpacePersonasScreen(
                    space = screen.space,
                    personas = personas,
                    onSelectPersonaForChat = { persona -> viewModel.openSpaceDirectChat(screen.space, persona) },
                    onCreatePersona = { viewModel.openCreateEditSpacePersona(screen.space) },
                    onEditPersona = { persona -> viewModel.openCreateEditSpacePersona(screen.space, persona) },
                    onDeletePersona = { persona -> viewModel.deleteSpacePersona(screen.space, persona) },
                    onViewProfile = { persona -> viewModel.openPersonaProfile(screen.space, persona) },
                    onViewMood = { persona -> viewModel.openPersonaMood(screen.space, persona) },
                    onBack = { viewModel.navigateBack() }
                )
            }
            is Screen.CreateEditSpacePersona -> {
                val personas by viewModel.observePersonas(screen.space.id).collectAsStateWithLifecycle(initialValue = emptyList())
                val otherPersonas = personas.filter { it.id != screen.personaToEdit?.id }

                CreateEditSpacePersonaScreen(
                    space = screen.space,
                    existingPersona = screen.personaToEdit,
                    existingUserCharacter = null,
                    otherPersonas = otherPersonas,
                    isUserCharacterMode = false,
                    prefillName = screen.prefillName,
                    onBack = { viewModel.navigateBack() },
                    onSavePersona = { persona -> viewModel.saveSpacePersona(screen.space, persona, isNew = screen.personaToEdit == null) },
                    onSaveUserCharacter = { /* not used in this mode */ },
                    onUploadImage = { personaId, kind, bytes, mimeType -> viewModel.uploadPersonaImage(screen.space.id, personaId, kind, bytes, mimeType) }
                )
            }
            is Screen.EditUserCharacter -> {
                val userCharacter by viewModel.observeUserCharacter(screen.space.id).collectAsStateWithLifecycle(initialValue = null)
                val places by viewModel.observePlaces(screen.space.id).collectAsStateWithLifecycle(initialValue = emptyList())

                CreateEditSpacePersonaScreen(
                    space = screen.space,
                    existingPersona = null,
                    existingUserCharacter = userCharacter,
                    otherPersonas = emptyList(),
                    isUserCharacterMode = true,
                    places = places,
                    onBack = { viewModel.navigateBack() },
                    onSavePersona = { /* not used in this mode */ },
                    onSaveUserCharacter = { character -> viewModel.saveUserCharacter(screen.space, character) },
                    onUploadImage = { _, _, _, _ -> Result.failure(IllegalStateException("Not supported in this mode")) }
                )
            }
            is Screen.Settings -> {
                SettingsScreen(
                    userConfig = userConfig,
                    soulRepository = viewModel.soulRepository,
                    onBack = { viewModel.navigateBack() },
                    onNavigateToSpacesBackendSettings = { viewModel.navigateToSpacesBackendSettings() },
                    onNavigateToMcpSettings = { viewModel.navigateToMcpSettings() },
                    onNavigateToLiteLlmServer = { viewModel.navigateToLiteLlmServer() },
                    onNavigateToChatModel = { viewModel.navigateToChatModel() },
                    llmConfig = llmConfig,
                    currentUserEmail = currentUser?.email,
                    onSignOut = { viewModel.signOut() }
                )
            }
            is Screen.SpacesBackendSettings -> {
                SpacesBackendSettingsScreen(
                    userConfig = userConfig,
                    soulRepository = viewModel.soulRepository,
                    onBack = { viewModel.navigateBack() }
                )
            }
            is Screen.McpSettings -> {
                McpSettingsScreen(
                    userConfig = userConfig,
                    soulRepository = viewModel.soulRepository,
                    onBack = { viewModel.navigateBack() }
                )
            }
            is Screen.LiteLlmServer -> {
                LiteLlmServerScreen(
                    llmConfig = llmConfig,
                    onTestConnection = { baseUrl -> viewModel.fetchLlmModels(baseUrl) },
                    onSave = { baseUrl, onDone -> viewModel.saveLlmBaseUrl(baseUrl, onDone) },
                    onBack = { viewModel.navigateBack() }
                )
            }
            is Screen.ChatModel -> {
                ChatModelScreen(
                    llmConfig = llmConfig,
                    onFetchModels = { baseUrl -> viewModel.fetchLlmModels(baseUrl) },
                    onSaveModel = { model, onDone -> viewModel.saveLlmModel(model, onDone) },
                    onBack = { viewModel.navigateBack() }
                )
            }
            is Screen.SpaceDirectChat -> {
                val messages by viewModel.observeDirectMessages(screen.space.id, screen.persona.id)
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                val needsInput by viewModel.observeNeedsInput(screen.space.id)
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                val pendingNeedsInput = needsInput.filter { it.targetChatPersonaId == screen.persona.id }
                val sendingPersonaIds by viewModel.sendingPersonaIds.collectAsStateWithLifecycle()
                val sendMessageErrors by viewModel.sendMessageErrors.collectAsStateWithLifecycle()
                val isSending = sendingPersonaIds.contains(screen.persona.id)
                val sendError = sendMessageErrors[screen.persona.id]

                SpaceDirectChatScreen(
                    space = screen.space,
                    persona = screen.persona,
                    messages = messages,
                    pendingNeedsInput = pendingNeedsInput,
                    isSending = isSending,
                    sendError = sendError,
                    onBack = { viewModel.navigateBack() },
                    onSendMessage = { text -> viewModel.sendSpaceDirectMessage(screen.space, screen.persona, text) },
                    onRetry = { viewModel.retryLastDirectMessage(screen.space, screen.persona) },
                    onCreatePersonaForNeedsInput = { targetName ->
                        viewModel.openCreateEditSpacePersona(screen.space, prefillName = targetName)
                    },
                    onDismissNeedsInput = { requestId -> viewModel.dismissNeedsInput(screen.space, requestId) },
                    onViewProfile = { viewModel.openPersonaProfile(screen.space, screen.persona) },
                    onEditPersona = { viewModel.openCreateEditSpacePersona(screen.space, screen.persona) },
                    onViewMood = { viewModel.openPersonaMood(screen.space, screen.persona) }
                )
            }
            is Screen.PersonaProfile -> {
                val personas by viewModel.observePersonas(screen.space.id).collectAsStateWithLifecycle(initialValue = emptyList())
                val otherPersonas = personas.filter { it.id != screen.persona.id }
                val userCharacter by viewModel.observeUserCharacter(screen.space.id).collectAsStateWithLifecycle(initialValue = null)
                val places by viewModel.observePlaces(screen.space.id).collectAsStateWithLifecycle(initialValue = emptyList())
                val activityLog by viewModel.observeActivityLog(screen.space.id, screen.persona.id).collectAsStateWithLifecycle(initialValue = emptyList())
                val storyFeed by viewModel.observeStoryFeed(screen.space.id).collectAsStateWithLifecycle(initialValue = emptyList())
                val livePersona = personas.find { it.id == screen.persona.id } ?: screen.persona
                val todoTasks = storyFeed.filter { it.speakerPersonaId == livePersona.id && it.status != "done" }

                SpacePersonaProfileScreen(
                    space = screen.space,
                    persona = livePersona,
                    otherPersonas = otherPersonas,
                    userCharacter = userCharacter,
                    places = places,
                    activityLog = activityLog,
                    todoTasks = todoTasks,
                    onBack = { viewModel.navigateBack() },
                    onMoveToPlace = { placeId -> viewModel.movePersonaToPlace(screen.space, livePersona, placeId) }
                )
            }
            is Screen.PersonaMood -> {
                val personas by viewModel.observePersonas(screen.space.id).collectAsStateWithLifecycle(initialValue = emptyList())
                val otherPersonas = personas.filter { it.id != screen.persona.id }
                val livePersona = personas.find { it.id == screen.persona.id } ?: screen.persona

                SpacePersonaMoodScreen(
                    persona = livePersona,
                    otherPersonas = otherPersonas,
                    onBack = { viewModel.navigateBack() }
                )
            }
            is Screen.SpacePlaces -> {
                val places by viewModel.observePlaces(screen.space.id).collectAsStateWithLifecycle(initialValue = emptyList())

                SpacePlacesScreen(
                    space = screen.space,
                    places = places,
                    onBack = { viewModel.navigateBack() },
                    onCreatePlace = { name, description -> viewModel.createPlace(screen.space, name, description) },
                    onDeletePlace = { place -> viewModel.deletePlace(screen.space, place) }
                )
            }
            is Screen.SpaceActivityLog -> {
                val activityLog by viewModel.observeSpaceActivityLog(screen.space.id).collectAsStateWithLifecycle(initialValue = emptyList())
                val liveSpace = spaces.find { it.id == screen.space.id } ?: screen.space

                SpaceActivityLogScreen(
                    space = liveSpace,
                    activityLog = activityLog,
                    onBack = { viewModel.navigateBack() }
                )
            }
            is Screen.GroupChatsList -> {
                val groupChats by viewModel.observeGroupChats(screen.space.id).collectAsStateWithLifecycle(initialValue = emptyList())
                val personas by viewModel.observePersonas(screen.space.id).collectAsStateWithLifecycle(initialValue = emptyList())

                GroupChatsListScreen(
                    space = screen.space,
                    groupChats = groupChats,
                    personas = personas,
                    onBack = { viewModel.navigateBack() },
                    onOpenGroupChat = { groupChat -> viewModel.openGroupChat(screen.space, groupChat) },
                    onCreateGroupChat = { name, personaIds -> viewModel.createGroupChat(screen.space, name, personaIds) },
                    onDeleteGroupChat = { groupChat -> viewModel.deleteGroupChat(screen.space, groupChat) }
                )
            }
            is Screen.GroupChat -> {
                val personas by viewModel.observePersonas(screen.space.id).collectAsStateWithLifecycle(initialValue = emptyList())
                val members = personas.filter { screen.groupChat.personaIds.contains(it.id) }
                val messages by viewModel.observeGroupChatMessages(screen.space.id, screen.groupChat.id)
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                val sendingGroupChatIds by viewModel.sendingGroupChatIds.collectAsStateWithLifecycle()
                val groupChatSendErrors by viewModel.groupChatSendErrors.collectAsStateWithLifecycle()
                val isSending = sendingGroupChatIds.contains(screen.groupChat.id)
                val sendError = groupChatSendErrors[screen.groupChat.id]

                GroupChatScreen(
                    groupChat = screen.groupChat,
                    members = members,
                    messages = messages,
                    isSending = isSending,
                    sendError = sendError,
                    onBack = { viewModel.navigateBack() },
                    onSendMessage = { text -> viewModel.sendGroupChatMessage(screen.space, screen.groupChat, text) }
                )
            }
            is Screen.StoryFeed -> {
                val tasks by viewModel.observeStoryFeed(screen.space.id).collectAsStateWithLifecycle(initialValue = emptyList())
                val personas by viewModel.observePersonas(screen.space.id).collectAsStateWithLifecycle(initialValue = emptyList())

                StoryFeedScreen(
                    space = screen.space,
                    tasks = tasks,
                    onBack = { viewModel.navigateBack() },
                    onTaskClick = { task ->
                        val persona = personas.find { it.id == task.speakerPersonaId }
                        if (persona != null) viewModel.openSpaceDirectChat(screen.space, persona)
                    }
                )
            }
        }
    }

    if (showCreateSpaceSheet) {
        CreateSpaceSheet(
            onDismiss = { viewModel.closeCreateSpaceSheet() },
            onCreate = { name, premise -> viewModel.createSpace(name, premise) },
            isSaving = isCreatingSpace
        )
    }
}
