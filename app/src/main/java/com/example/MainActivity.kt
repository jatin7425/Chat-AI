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
import com.example.ui.chat.ChatScreen
import com.example.ui.chat.DualPersonaChatScreen
import com.example.ui.memory.MemoryRecapScreen
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.personas.CreatePersonaSheet
import com.example.ui.personas.PersonasScreen
import com.example.ui.settings.ChatModelScreen
import com.example.ui.settings.LiteLlmServerScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SpacesBackendSettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.NotificationHelper

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        NotificationHelper.createNotificationChannel(this)
        requestNotificationPermission()
        handleIntent(intent)

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
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == NotificationHelper.ACTION_OPEN_CHAT) {
            val personaId = intent.getLongExtra(NotificationHelper.EXTRA_PERSONA_ID, -1L)
            if (personaId != -1L) {
                viewModel.openChatByPersonaId(personaId)
            }
        }
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
    val personas by viewModel.personasState.collectAsStateWithLifecycle()
    val userConfig by viewModel.userConfigState.collectAsStateWithLifecycle()
    val showCreateSheet by viewModel.showCreateSheet.collectAsStateWithLifecycle()
    val editingPersona by viewModel.editingPersona.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

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
                com.example.ui.auth.AuthScreen(
                    authRepository = viewModel.authRepository,
                    onAuthenticated = { /* no-op: MainViewModel's currentUser collector drives routing */ }
                )
            }
            is Screen.Onboarding -> {
                OnboardingScreen(
                    soulRepository = viewModel.soulRepository,
                    onCompleteOnboarding = { userName, userBio, baseUrl, persona ->
                        viewModel.completeOnboarding(userName, userBio, baseUrl, persona)
                    }
                )
            }
            is Screen.Personas -> {
                PersonasScreen(
                    personas = personas,
                    userConfig = userConfig,
                    onSelectPersonaForChat = { persona -> viewModel.openChatWithPersona(persona) },
                    onOpenMemoryRecap = { persona -> viewModel.openMemoryRecap(persona) },
                    onCreateNewPersona = { viewModel.openCreatePersonaSheet(null) },
                    onEditPersona = { persona -> viewModel.openCreatePersonaSheet(persona) },
                    onDeletePersona = { persona -> viewModel.deletePersona(persona) },
                    onOpenSettings = { viewModel.navigateToSettings() },
                    onStartDualChat = { pA, pB -> viewModel.openDualPersonaChat(pA, pB) },
                    soulRepository = viewModel.soulRepository
                )
            }
            is Screen.Chat -> {
                val messages by viewModel.getMessagesForSession(screen.sessionId)
                    .collectAsStateWithLifecycle(initialValue = emptyList())

                ChatScreen(
                    persona = screen.persona,
                    sessionId = screen.sessionId,
                    messages = messages,
                    onBack = { viewModel.navigateToPersonas() },
                    onEditPersona = { persona -> viewModel.openCreatePersonaSheet(persona) },
                    onOpenMemoryRecap = { persona -> viewModel.openMemoryRecap(persona) },
                    onSendMessage = { msg, onResult ->
                        viewModel.sendMessage(screen.sessionId, screen.persona, msg, onResult)
                    },
                    onRetryMessage = {
                        viewModel.retryLastMessage(screen.sessionId, screen.persona)
                    },
                    onStartDualChat = { pA, pB ->
                        viewModel.openDualPersonaChat(pA, pB)
                    },
                    allPersonas = personas,
                    soulRepository = viewModel.soulRepository
                )
            }
            is Screen.DualPersonaChat -> {
                val messages by viewModel.getMessagesForSession(screen.sessionId)
                    .collectAsStateWithLifecycle(initialValue = emptyList())

                DualPersonaChatScreen(
                    personaA = screen.personaA,
                    personaB = screen.personaB,
                    sessionId = screen.sessionId,
                    messages = messages,
                    onBack = { viewModel.navigateToPersonas() },
                    onNextTurn = { onResult ->
                        viewModel.sendDualPersonaTurn(screen.personaA, screen.personaB, screen.sessionId, onResult)
                    },
                    soulRepository = viewModel.soulRepository
                )
            }
            is Screen.MemoryRecap -> {
                MemoryRecapScreen(
                    persona = screen.persona,
                    soulRepository = viewModel.soulRepository,
                    onBack = { viewModel.navigateToPersonas() }
                )
            }
            is Screen.Settings -> {
                SettingsScreen(
                    userConfig = userConfig,
                    soulRepository = viewModel.soulRepository,
                    onBack = { viewModel.navigateToPersonas() },
                    onNavigateToLiteLlmServer = { viewModel.navigateToLiteLlmServer() },
                    onNavigateToChatModel = { viewModel.navigateToChatModel() },
                    onNavigateToSpacesBackendSettings = { viewModel.navigateToSpacesBackendSettings() },
                    currentUserEmail = currentUser?.email,
                    onSignOut = { viewModel.signOut() }
                )
            }
            is Screen.LiteLlmServer -> {
                LiteLlmServerScreen(
                    userConfig = userConfig,
                    soulRepository = viewModel.soulRepository,
                    onBack = { viewModel.navigateToSettings() }
                )
            }
            is Screen.ChatModel -> {
                ChatModelScreen(
                    userConfig = userConfig,
                    soulRepository = viewModel.soulRepository,
                    onBack = { viewModel.navigateToSettings() }
                )
            }
            is Screen.SpacesBackendSettings -> {
                SpacesBackendSettingsScreen(
                    userConfig = userConfig,
                    soulRepository = viewModel.soulRepository,
                    onBack = { viewModel.navigateToSettings() }
                )
            }
        }
    }

    if (showCreateSheet) {
        CreatePersonaSheet(
            existingPersona = editingPersona,
            soulRepository = viewModel.soulRepository,
            onDismiss = { viewModel.closeCreatePersonaSheet() },
            onSave = { persona -> viewModel.savePersona(persona) }
        )
    }
}
