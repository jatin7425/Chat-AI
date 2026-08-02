package com.example.ui.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserConfigEntity
import com.example.data.repository.SoulRepository
import com.example.data.spaces.SpacesApiClient
import com.example.data.spaces.model.LlmConfigModel
import com.example.ui.theme.customTextFieldColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userConfig: UserConfigEntity?,
    soulRepository: SoulRepository,
    onBack: () -> Unit,
    onNavigateToSpacesBackendSettings: () -> Unit = {},
    onNavigateToLiteLlmServer: () -> Unit = {},
    onNavigateToChatModel: () -> Unit = {},
    llmConfig: LlmConfigModel = LlmConfigModel(),
    currentUserEmail: String? = null,
    onSignOut: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    var isDarkMode by remember { mutableStateOf(userConfig?.darkTheme ?: true) }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 12.dp),
                title = {
                    Text(
                        "Settings",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsMenuCard(
                title = "Signed in as",
                subtitle = currentUserEmail ?: "Unknown",
                onClick = {},
                showChevron = false,
                testTag = "settings_account_item"
            )
            SettingsMenuCard(
                title = "Sign Out",
                subtitle = "You'll need to sign in again to continue",
                onClick = onSignOut,
                showChevron = false,
                testTag = "settings_sign_out_item"
            )

            SettingsMenuCard(
                title = "Backend Server",
                subtitle = SpacesApiClient.effectiveBaseUrl(userConfig?.spacesApiBaseUrl ?: "").ifBlank { "Not configured" },
                onClick = onNavigateToSpacesBackendSettings,
                testTag = "settings_backend_server_item"
            )

            SettingsMenuCard(
                title = "LiteLLM Server",
                subtitle = llmConfig.llmBaseUrl.ifBlank { "Not configured" },
                onClick = onNavigateToLiteLlmServer,
                testTag = "settings_litellm_server_item"
            )

            SettingsMenuCard(
                title = "Default Model",
                subtitle = llmConfig.llmModel.ifBlank { "Not selected" },
                onClick = onNavigateToChatModel,
                testTag = "settings_default_model_item"
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dark Mode",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )

                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = { checked ->
                            isDarkMode = checked
                            coroutineScope.launch {
                                val current = soulRepository.getUserConfig()
                                soulRepository.saveUserConfig(current.copy(darkTheme = checked))
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsMenuCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showChevron: Boolean = true,
    testTag: String = ""
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag(testTag),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }

            if (showChevron) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpacesBackendSettingsScreen(
    userConfig: UserConfigEntity?,
    soulRepository: SoulRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Only an explicit persisted override -- left blank if the user has never saved one, so
    // the build-injected default (dev tunnel URL on debug, CI-supplied URL on release) keeps
    // being used transparently rather than getting "frozen" into storage the first time this
    // screen renders.
    var baseUrl by remember { mutableStateOf(userConfig?.spacesApiBaseUrl ?: "") }
    var isLoading by remember { mutableStateOf(false) }
    val buildDefaultUrl = remember { SpacesApiClient.effectiveBaseUrl("") }

    // Convenience presets for this dev setup -- LAN only works on the same Wi-Fi as the backend
    // machine and breaks if its IP changes. "Custom" just leaves the field free-typed. (A public
    // tunnel preset was tried and dropped -- localtunnel's free subdomains aren't stable across
    // reconnects, so it silently pointed at the wrong URL after any restart.)
    val presets = remember(buildDefaultUrl) {
        listOf(
            "LAN (same Wi-Fi only)" to buildDefaultUrl,
            "Custom" to null
        )
    }
    var presetsExpanded by remember { mutableStateOf(false) }
    var selectedPresetLabel by remember(baseUrl, presets) {
        mutableStateOf(presets.firstOrNull { it.second != null && it.second == baseUrl }?.first ?: "Custom")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 12.dp),
                title = {
                    Text(
                        "Backend Server",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Connect via",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )

            ExposedDropdownMenuBox(
                expanded = presetsExpanded,
                onExpandedChange = { presetsExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedPresetLabel,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = presetsExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = RoundedCornerShape(12.dp),
                    colors = customTextFieldColors()
                )
                ExposedDropdownMenu(expanded = presetsExpanded, onDismissRequest = { presetsExpanded = false }) {
                    presets.forEach { (label, url) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                selectedPresetLabel = label
                                if (url != null) baseUrl = url
                                presetsExpanded = false
                            }
                        )
                    }
                }
            }

            Text(
                text = "Base URL",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                placeholder = {
                    Text(
                        buildDefaultUrl.ifBlank { "https://your-dev-tunnel-url.com" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("backend_base_url_input"),
                shape = RoundedCornerShape(12.dp),
                colors = customTextFieldColors(),
                singleLine = true
            )

            Text(
                text = if (buildDefaultUrl.isNotBlank()) {
                    "Leave blank to use the build-injected default shown above (the dev tunnel URL on debug builds, or the URL supplied at release-build time). Enter a URL here only to override it."
                } else {
                    "The URL used to reach the Spaces backend -- during development this is your dev tunnel's public URL (ngrok, Cloudflare Tunnel, VS Code dev tunnels, etc.), since the backend only runs locally on your machine for now."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            isLoading = true
                            val res = SpacesApiClient.healthCheck(SpacesApiClient.effectiveBaseUrl(baseUrl))
                            isLoading = false
                            if (res.isSuccess) {
                                Toast.makeText(context, "Connection successful!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Connection failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Test Connection", fontWeight = FontWeight.SemiBold)
                    }
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            soulRepository.updateSpacesApiBaseUrl(baseUrl.trim())
                            Toast.makeText(context, "Backend URL saved!", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("save_backend_url_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
