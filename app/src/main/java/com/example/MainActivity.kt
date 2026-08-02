package com.example

import android.Manifest
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
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SpacesBackendSettingsScreen
import com.example.ui.spaces.dashboard.CreateSpaceSheet
import com.example.ui.spaces.dashboard.SpacesDashboardScreen
import com.example.ui.spaces.home.SpaceHomeScreen
import com.example.ui.spaces.personas.CreateEditSpacePersonaScreen
import com.example.ui.spaces.personas.SpacePersonasScreen
import com.example.ui.theme.MyApplicationTheme

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
                SpacesDashboardScreen(
                    spaces = spaces,
                    onOpenSpace = { space -> viewModel.openSpace(space) },
                    onCreateSpace = { viewModel.openCreateSpaceSheet() },
                    onOpenSettings = { viewModel.navigateToSettings() }
                )
            }
            is Screen.SpaceHome -> {
                val personas by viewModel.observePersonas(screen.space.id).collectAsStateWithLifecycle(initialValue = emptyList())
                val userCharacter by viewModel.observeUserCharacter(screen.space.id).collectAsStateWithLifecycle(initialValue = null)

                SpaceHomeScreen(
                    space = screen.space,
                    userCharacter = userCharacter,
                    personas = personas,
                    onBack = { viewModel.navigateBack() },
                    onToggleSim = { viewModel.toggleSimStatus(screen.space) },
                    onEditUserCharacter = { viewModel.openEditUserCharacter(screen.space) },
                    onOpenPersonas = { viewModel.openSpacePersonas(screen.space) },
                    onOpenPersonaChat = { /* wired in Phase 4 (Direct Chat) */ }
                )
            }
            is Screen.SpacePersonas -> {
                val personas by viewModel.observePersonas(screen.space.id).collectAsStateWithLifecycle(initialValue = emptyList())

                SpacePersonasScreen(
                    space = screen.space,
                    personas = personas,
                    onSelectPersonaForChat = { /* wired in Phase 4 (Direct Chat) */ },
                    onCreatePersona = { viewModel.openCreateEditSpacePersona(screen.space) },
                    onEditPersona = { persona -> viewModel.openCreateEditSpacePersona(screen.space, persona) },
                    onDeletePersona = { persona -> viewModel.deleteSpacePersona(screen.space, persona) },
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
                    onBack = { viewModel.navigateBack() },
                    onSavePersona = { persona -> viewModel.saveSpacePersona(screen.space, persona) },
                    onSaveUserCharacter = { /* not used in this mode */ },
                    onAnalyzePhoto = { imageBase64, mimeType -> viewModel.analyzePersonaPhoto(screen.space, imageBase64, mimeType) }
                )
            }
            is Screen.EditUserCharacter -> {
                val userCharacter by viewModel.observeUserCharacter(screen.space.id).collectAsStateWithLifecycle(initialValue = null)

                CreateEditSpacePersonaScreen(
                    space = screen.space,
                    existingPersona = null,
                    existingUserCharacter = userCharacter,
                    otherPersonas = emptyList(),
                    isUserCharacterMode = true,
                    onBack = { viewModel.navigateBack() },
                    onSavePersona = { /* not used in this mode */ },
                    onSaveUserCharacter = { character -> viewModel.saveUserCharacter(screen.space, character) },
                    onAnalyzePhoto = { imageBase64, mimeType -> viewModel.analyzePersonaPhoto(screen.space, imageBase64, mimeType) }
                )
            }
            is Screen.Settings -> {
                SettingsScreen(
                    userConfig = userConfig,
                    soulRepository = viewModel.soulRepository,
                    onBack = { viewModel.navigateBack() },
                    onNavigateToSpacesBackendSettings = { viewModel.navigateToSpacesBackendSettings() },
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
