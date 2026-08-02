package com.example.ui.onboarding

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PersonaEntity
import com.example.data.repository.SoulRepository
import com.example.ui.components.AvatarView
import com.example.ui.theme.customTextFieldColors
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    soulRepository: SoulRepository,
    onCompleteOnboarding: (userName: String, userBio: String, baseUrl: String, persona: PersonaEntity) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentStep by remember { mutableStateOf(1) } // Step 1: User Details, Step 2: LiteLLM, Step 3: First Persona

    // Step 1 State: User Details
    var userName by remember { mutableStateOf("") }
    var userBio by remember { mutableStateOf("") }

    // Step 2 State: LiteLLM Server
    var baseUrl by remember { mutableStateOf("") }
    var isTestingConnection by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf<String?>(null) }
    var isConnectionSuccess by remember { mutableStateOf<Boolean?>(null) }

    // Step 3 State: Persona Creation
    var useTemplatePersona by remember { mutableStateOf(true) }
    var personaName by remember { mutableStateOf("Araa") }
    var personaRelationship by remember { mutableStateOf("Friend") }
    var personaGender by remember { mutableStateOf("Female") }
    var personaAvatarStyle by remember { mutableStateOf("Avataaars (Modern)") }
    var personaTraits by remember { mutableStateOf("Caring, Empathetic, Playful, Affectionate") }
    var personaBio by remember { mutableStateOf("Araa is your best friend and caring companion. She checks in on you, listens without judgment, and offers warm, gentle comfort when you feel stressed.") }

    val relationships = listOf("Friend", "Girlfriend", "Boyfriend", "Mentor", "Counselor", "Partner")
    val avatarStyles = listOf("Avataaars (Modern)", "DiceBear / Cartoon", "Realistic", "Pixel Art")

    var showImportDialog by remember { mutableStateOf(false) }
    var showReceiveDialog by remember { mutableStateOf(false) }

    fun handleImportSuccess() {
        coroutineScope.launch {
            val config = soulRepository.getUserConfig()
            val personas = soulRepository.allPersonas.firstOrNull() ?: emptyList()
            val defaultPersona = personas.firstOrNull() ?: PersonaEntity(
                name = "Araa",
                relationship = "Friend",
                gender = "Female",
                avatarStyle = "Avataaars (Modern)",
                avatarSeed = "Araa",
                traits = "Caring, Empathetic, Playful",
                bio = "Araa is your best friend.",
                isDefault = true
            )
            onCompleteOnboarding(
                config.userName.ifBlank { "User" },
                config.userBio,
                config.baseUrl,
                defaultPersona
            )
        }
    }

    if (showImportDialog) {
        com.example.ui.components.ImportBackupDialog(
            soulRepository = soulRepository,
            onDismiss = { showImportDialog = false },
            onSuccess = {
                showImportDialog = false
                handleImportSuccess()
            }
        )
    }

    if (showReceiveDialog) {
        com.example.ui.components.ReceiveLocalDataDialog(
            soulRepository = soulRepository,
            onDismiss = { showReceiveDialog = false },
            onSuccess = {
                showReceiveDialog = false
                handleImportSuccess()
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Header / Brand Logo
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Soul AI Companion",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Restore / Import Quick Banner on Onboarding launch
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Have a backup?",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = { showReceiveDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Receive Wi-Fi", fontSize = 11.sp)
                        }

                        TextButton(
                            onClick = { showImportDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import JSON", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Step Progress Indicator Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(3) { stepIdx ->
                    val isCompleted = stepIdx + 1 <= currentStep
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isCompleted) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }

            Text(
                text = when (currentStep) {
                    1 -> "Step 1 of 3 • Personal Details"
                    2 -> "Step 2 of 3 • LiteLLM Server Setup"
                    else -> "Step 3 of 3 • Create Your AI Companion"
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Step Contents
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { width -> width / 3 } + fadeIn() + scaleIn(initialScale = 0.96f))
                                .togetherWith(slideOutHorizontally { width -> -width / 3 } + fadeOut())
                        } else {
                            (slideInHorizontally { width -> -width / 3 } + fadeIn() + scaleIn(initialScale = 0.96f))
                                .togetherWith(slideOutHorizontally { width -> width / 3 } + fadeOut())
                        }
                    },
                    label = "onboarding_step_transition"
                ) { step ->
                    when (step) {
                        1 -> StepUserDetails(
                            userName = userName,
                            onUserNameChange = { userName = it },
                            userBio = userBio,
                            onUserBioChange = { userBio = it }
                        )
                        2 -> StepLiteLlmSetup(
                            baseUrl = baseUrl,
                            onBaseUrlChange = { baseUrl = it },
                            isTesting = isTestingConnection,
                            statusMessage = connectionStatus,
                            isSuccess = isConnectionSuccess,
                            onTestConnection = {
                                val urlToTest = baseUrl.trim()
                                isTestingConnection = true
                                connectionStatus = if (urlToTest.isBlank()) "Please enter a Base URL first" else "Connecting to $urlToTest..."
                                isConnectionSuccess = null
                                if (urlToTest.isBlank()) {
                                    isTestingConnection = false
                                    isConnectionSuccess = false
                                } else {
                                    coroutineScope.launch {
                                        val res = soulRepository.fetchAvailableModels(urlToTest)
                                        isTestingConnection = false
                                        if (res.isSuccess) {
                                            val models = res.getOrDefault(emptyList())
                                            isConnectionSuccess = true
                                            connectionStatus = "Connected successfully! (${models.size} models available)"
                                        } else {
                                            isConnectionSuccess = false
                                            connectionStatus = "Connection failed: ${res.exceptionOrNull()?.localizedMessage ?: "Unknown error"}"
                                        }
                                    }
                                }
                            }
                        )
                        3 -> StepCreatePersona(
                            useTemplate = useTemplatePersona,
                            onToggleUseTemplate = { useTemplatePersona = it },
                            name = personaName,
                            onNameChange = { personaName = it },
                            relationship = personaRelationship,
                            onRelationshipChange = { personaRelationship = it },
                            gender = personaGender,
                            onGenderChange = { personaGender = it },
                            avatarStyle = personaAvatarStyle,
                            onAvatarStyleChange = { personaAvatarStyle = it },
                            traits = personaTraits,
                            onTraitsChange = { personaTraits = it },
                            bio = personaBio,
                            onBioChange = { personaBio = it },
                            relationships = relationships,
                            avatarStyles = avatarStyles
                        )
                    }
                }
            }

            // Bottom Navigation Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 1) {
                    OutlinedButton(
                        onClick = { currentStep -= 1 },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Back")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (currentStep < 3) {
                    Button(
                        onClick = {
                            if (currentStep == 1 && userName.isBlank()) {
                                Toast.makeText(context, "Please enter your name", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            currentStep += 1
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("onboarding_next_button")
                    ) {
                        Text("Next")
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                } else {
                    Button(
                        onClick = {
                            if (userName.isBlank()) {
                                Toast.makeText(context, "Please enter your name", Toast.LENGTH_SHORT).show()
                                currentStep = 1
                                return@Button
                            }
                            if (personaName.isBlank()) {
                                Toast.makeText(context, "Please name your AI companion", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val createdPersona = PersonaEntity(
                                name = personaName.trim(),
                                age = 24,
                                gender = personaGender,
                                relationship = personaRelationship,
                                avatarStyle = personaAvatarStyle,
                                avatarSeed = personaName.trim(),
                                traits = personaTraits,
                                eyeColor = "Warm Brown",
                                hairStyle = "Casual",
                                height = "5'7",
                                build = "Average",
                                clothingStyle = "Casual Stylish",
                                keyFeatures = "Warm expressive eyes",
                                bio = personaBio.ifBlank { "Your supportive companion ready to chat whenever you need." },
                                emotionsJson = """[{"emotion":"Care","percentage":90},{"emotion":"Affection","percentage":85}]""",
                                isDefault = true
                            )

                            val effectiveBaseUrl = baseUrl.trim()
                            onCompleteOnboarding(userName.trim(), userBio.trim(), effectiveBaseUrl, createdPersona)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("onboarding_finish_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Finish Setup & Start Chatting")
                    }
                }
            }
        }
    }
}

@Composable
private fun StepUserDetails(
    userName: String,
    onUserNameChange: (String) -> Unit,
    userBio: String,
    onUserBioChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Welcome to Soul AI",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Let's personalize your companion experience. How should your AI companions address you?",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )

        OutlinedTextField(
            value = userName,
            onValueChange = onUserNameChange,
            label = { Text("Your Name") },
            placeholder = { Text("e.g., Jatin, Alex") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_name_input"),
            colors = customTextFieldColors(),
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = userBio,
            onValueChange = onUserBioChange,
            label = { Text("About You / Your Expectations") },
            placeholder = { Text("e.g. Looking for emotional support, goal accountability, and casual conversations...") },
            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
            minLines = 3,
            maxLines = 5,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_bio_input"),
            colors = customTextFieldColors(),
            shape = RoundedCornerShape(12.dp)
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Your personal profile stays completely private on your device and is only used to personalize responses.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun StepLiteLlmSetup(
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    isTesting: Boolean,
    statusMessage: String?,
    isSuccess: Boolean?,
    onTestConnection: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Connect to LiteLLM",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Soul AI connects to your custom LiteLLM server or proxy endpoint for flexible model choices.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )

        OutlinedTextField(
            value = baseUrl,
            onValueChange = onBaseUrlChange,
            label = { Text("LiteLLM Base URL") },
            placeholder = { Text("https://your-llm-server.com") },
            leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_url_input"),
            colors = customTextFieldColors(),
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedButton(
            onClick = onTestConnection,
            enabled = !isTesting,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_test_connection_button")
        ) {
            if (isTesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Testing Connection...")
            } else {
                Icon(Icons.Default.CloudSync, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Test Connection")
            }
        }

        if (statusMessage != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = when (isSuccess) {
                    true -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    false -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                border = BorderStroke(
                    1.dp,
                    when (isSuccess) {
                        true -> MaterialTheme.colorScheme.primary
                        false -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.outline
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (isSuccess) {
                            true -> Icons.Default.CheckCircle
                            false -> Icons.Default.Error
                            else -> Icons.Default.Info
                        },
                        contentDescription = null,
                        tint = when (isSuccess) {
                            true -> MaterialTheme.colorScheme.primary
                            false -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = statusMessage,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Text(
            text = "Enter your custom LiteLLM or OpenAI-compatible server URL.\nYou can update this anytime later in Settings.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun StepCreatePersona(
    useTemplate: Boolean,
    onToggleUseTemplate: (Boolean) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    relationship: String,
    onRelationshipChange: (String) -> Unit,
    gender: String,
    onGenderChange: (String) -> Unit,
    avatarStyle: String,
    onAvatarStyleChange: (String) -> Unit,
    traits: String,
    onTraitsChange: (String) -> Unit,
    bio: String,
    onBioChange: (String) -> Unit,
    relationships: List<String>,
    avatarStyles: List<String>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Your First Persona",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Template Choice Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (useTemplate) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    if (useTemplate) 2.dp else 1.dp,
                    if (useTemplate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        onToggleUseTemplate(true)
                        onNameChange("Araa")
                        onRelationshipChange("Friend")
                        onGenderChange("Female")
                        onAvatarStyleChange("Avataaars (Modern)")
                        onTraitsChange("Caring, Empathetic, Playful, Affectionate")
                        onBioChange("Araa is your best friend and caring companion. She checks in on you, listens without judgment, and offers warm, gentle comfort when you feel stressed.")
                    }
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AvatarView(name = "Araa", avatarStyle = "Avataaars (Modern)", avatarSeed = "Araa", size = 48.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Araa (Default)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Best Friend & Companion", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (!useTemplate) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    if (!useTemplate) 2.dp else 1.dp,
                    if (!useTemplate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        onToggleUseTemplate(false)
                        if (name == "Araa") {
                            onNameChange("Companion")
                        }
                    }
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AvatarView(
                        name = if (name.isBlank()) "Companion" else name,
                        avatarStyle = avatarStyle,
                        avatarSeed = name,
                        size = 48.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Custom Persona", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Build from Scratch", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Preview & Form
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarView(
                        name = name.ifBlank { "Companion" },
                        avatarStyle = avatarStyle,
                        avatarSeed = name,
                        size = 54.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = name.ifBlank { "Companion Name" },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$relationship • $gender",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Companion Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = customTextFieldColors()
                )

                Text("Relationship", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    relationships.take(4).forEach { rel ->
                        FilterChip(
                            selected = relationship == rel,
                            onClick = { onRelationshipChange(rel) },
                            label = { Text(rel, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = traits,
                    onValueChange = onTraitsChange,
                    label = { Text("Personality Traits") },
                    placeholder = { Text("e.g. Caring, Wise, Sarcastic") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = customTextFieldColors()
                )

                OutlinedTextField(
                    value = bio,
                    onValueChange = onBioChange,
                    label = { Text("Bio / Behavior Context") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    colors = customTextFieldColors()
                )
            }
        }
    }
}
