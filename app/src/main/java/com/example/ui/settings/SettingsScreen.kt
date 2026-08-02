package com.example.ui.settings

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserConfigEntity
import com.example.data.repository.SoulRepository
import com.example.ui.theme.customTextFieldColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userConfig: UserConfigEntity?,
    soulRepository: SoulRepository,
    onBack: () -> Unit,
    onNavigateToLiteLlmServer: () -> Unit,
    onNavigateToChatModel: () -> Unit,
    currentUserEmail: String? = null,
    onSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isDarkMode by remember { mutableStateOf(userConfig?.darkTheme ?: true) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showReceiveDialog by remember { mutableStateOf(false) }

    if (showExportDialog) {
        com.example.ui.components.ExportBackupDialog(
            soulRepository = soulRepository,
            onDismiss = { showExportDialog = false }
        )
    }

    if (showImportDialog) {
        com.example.ui.components.ImportBackupDialog(
            soulRepository = soulRepository,
            onDismiss = { showImportDialog = false },
            onSuccess = { showImportDialog = false }
        )
    }

    if (showReceiveDialog) {
        com.example.ui.components.ReceiveLocalDataDialog(
            soulRepository = soulRepository,
            onDismiss = { showReceiveDialog = false },
            onSuccess = { showReceiveDialog = false }
        )
    }

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
            // Account item (minimal for now; fleshed out into a full Account section later)
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

            // LiteLLM Server item
            SettingsMenuCard(
                title = "LiteLLM Server",
                subtitle = userConfig?.baseUrl?.ifBlank { "Not configured" } ?: "Not configured",
                onClick = onNavigateToLiteLlmServer,
                testTag = "settings_litellm_server_item"
            )

            // Default Model item
            SettingsMenuCard(
                title = "Default Model",
                subtitle = userConfig?.selectedModel?.ifBlank { "gpt-4o-mini" } ?: "gpt-4o-mini",
                onClick = onNavigateToChatModel,
                testTag = "settings_default_model_item"
            )

            // Dark Mode item
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

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Data Backup & Local Transfer",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
            )

            // Wi-Fi / Hotspot Sender Server Card
            com.example.ui.components.SenderServerCard(soulRepository = soulRepository)

            // Receive via Wi-Fi Card
            SettingsMenuCard(
                title = "Receive Data via Local Network / Hotspot",
                subtitle = "Connect to Sender device IP to download backup",
                onClick = { showReceiveDialog = true },
                testTag = "settings_receive_data_item"
            )

            // Export Backup Card
            SettingsMenuCard(
                title = "Export Backup (JSON)",
                subtitle = "Copy or export complete chat history and personas",
                onClick = { showExportDialog = true },
                testTag = "settings_export_backup_item"
            )

            // Import Backup Card
            SettingsMenuCard(
                title = "Import Backup (JSON)",
                subtitle = "Restore data from a JSON backup payload",
                onClick = { showImportDialog = true },
                testTag = "settings_import_backup_item"
            )

            // About item
            SettingsMenuCard(
                title = "About",
                subtitle = "Persona Chat - v1.0.0",
                onClick = {
                    Toast.makeText(context, "Persona Chat v1.0.0 — AI Emotional Companions", Toast.LENGTH_SHORT).show()
                },
                showChevron = false,
                testTag = "settings_about_item"
            )
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
fun LiteLlmServerScreen(
    userConfig: UserConfigEntity?,
    soulRepository: SoulRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var baseUrl by remember { mutableStateOf(userConfig?.baseUrl ?: "") }
    var isLoading by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 12.dp),
                title = {
                    Text(
                        "LiteLLM Server",
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
                text = "Base URL",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                placeholder = { Text("https://your-llm-server.com", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("base_url_input"),
                shape = RoundedCornerShape(12.dp),
                colors = customTextFieldColors(),
                singleLine = true
            )

            Text(
                text = "The base URL used to reach your LiteLLM proxy for chat completions and model discovery.",
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
                            val res = soulRepository.fetchAvailableModels(baseUrl)
                            isLoading = false
                            if (res.isSuccess) {
                                val count = res.getOrDefault(emptyList()).size
                                Toast.makeText(context, "Connection successful! Fetched $count models.", Toast.LENGTH_SHORT).show()
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
                            val current = soulRepository.getUserConfig()
                            soulRepository.saveUserConfig(current.copy(baseUrl = baseUrl))
                            Toast.makeText(context, "Base URL saved!", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("save_server_url_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatModelScreen(
    userConfig: UserConfigEntity?,
    soulRepository: SoulRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val baseUrl = userConfig?.baseUrl.orEmpty()
    var selectedModel by remember { mutableStateOf(userConfig?.selectedModel?.ifBlank { "gpt-4o-mini" } ?: "gpt-4o-mini") }
    var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var customModelInput by remember { mutableStateOf("") }

    LaunchedEffect(baseUrl) {
        isLoading = true
        val res = soulRepository.fetchAvailableModels(baseUrl)
        isLoading = false
        val fetched = res.getOrDefault(emptyList())
        // Render ONLY models received from the model API
        availableModels = fetched
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 12.dp),
                title = {
                    Text(
                        "Chat Model",
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
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                isLoading = true
                                val res = soulRepository.fetchAvailableModels(baseUrl)
                                isLoading = false
                                availableModels = res.getOrDefault(emptyList())
                                Toast.makeText(context, "Refreshed model list", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh Models",
                            tint = MaterialTheme.colorScheme.primary
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Fetched from API: $baseUrl",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (availableModels.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "No models received from API at $baseUrl",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "You can manually specify a custom model name below:",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )

                    OutlinedTextField(
                        value = customModelInput,
                        onValueChange = { customModelInput = it },
                        placeholder = { Text("e.g. gpt-4o-mini or nvidia") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = customTextFieldColors(),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            if (customModelInput.isNotBlank()) {
                                selectedModel = customModelInput.trim()
                                coroutineScope.launch {
                                    val current = soulRepository.getUserConfig()
                                    soulRepository.saveUserConfig(current.copy(selectedModel = selectedModel))
                                    Toast.makeText(context, "Saved model: $selectedModel", Toast.LENGTH_SHORT).show()
                                    onBack()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Model Name", color = Color.White)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(availableModels) { modelName ->
                        val isSelected = modelName == selectedModel

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    selectedModel = modelName
                                    coroutineScope.launch {
                                        val current = soulRepository.getUserConfig()
                                        soulRepository.saveUserConfig(current.copy(selectedModel = modelName))
                                        Toast.makeText(context, "Selected model: $modelName", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .testTag("model_option_$modelName"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = modelName,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "API Model",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .border(
                                            1.5.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color.White)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
