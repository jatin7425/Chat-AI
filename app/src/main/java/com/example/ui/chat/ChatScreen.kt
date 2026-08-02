package com.example.ui.chat

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ChatMessageEntity
import com.example.data.model.PersonaEntity
import com.example.data.repository.SoulRepository
import com.example.ui.components.AvatarView
import com.example.ui.theme.customTextFieldColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    persona: PersonaEntity,
    sessionId: String,
    messages: List<ChatMessageEntity>,
    onBack: () -> Unit,
    onEditPersona: (PersonaEntity) -> Unit,
    onOpenMemoryRecap: (PersonaEntity) -> Unit,
    onSendMessage: ((String, (Result<String>) -> Unit) -> Unit)? = null,
    onRetryMessage: (() -> Unit)? = null,
    onStartDualChat: ((PersonaEntity, PersonaEntity) -> Unit)? = null,
    allPersonas: List<PersonaEntity> = emptyList(),
    soulRepository: SoulRepository
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current

    var inputText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showEmotionDialog by remember { mutableStateOf(false) }
    var showInviteSheet by remember { mutableStateOf(false) }

    // Observe live persona state from database for AI emotional attachment updates
    val livePersonaFlow = remember(persona.id) { soulRepository.getPersonaByIdFlow(persona.id) }
    val livePersonaState by livePersonaFlow.collectAsStateWithLifecycle(initialValue = persona)
    val currentPersona = livePersonaState ?: persona

    // Parse emotions for header status badge
    val emotions = remember(currentPersona.emotionsJson) {
        soulRepository.parseEmotions(currentPersona.emotionsJson)
    }
    val moodStatus = if (emotions.isNotEmpty()) {
        emotions.joinToString(" • ") { "${it.emotion} ${it.percentage}%" }
    } else {
        "Neutral & Calming"
    }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size, isSending) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Quick prompts
    val quickPrompts = remember(persona.relationship) {
        when {
            persona.relationship.contains("Girlfriend", ignoreCase = true) ||
                    persona.relationship.contains("Boyfriend", ignoreCase = true) -> listOf(
                "Hey, how's your day going?",
                "I miss talking with you...",
                "Can you cheer me up today?"
            )
            persona.relationship.contains("Counselor", ignoreCase = true) -> listOf(
                "I feel overwhelmed today...",
                "Can we do a quick grounding exercise?",
                "How do I manage anxiety better?"
            )
            persona.relationship.contains("Mentor", ignoreCase = true) -> listOf(
                "I need some career advice...",
                "How do I stay disciplined?",
                "Help me plan my goals."
            )
            else -> listOf(
                "Tell me something interesting!",
                "I had a long day, let's talk.",
                "Give me some good advice."
            )
        }
    }

    fun handleSend(text: String) {
        if (text.isBlank() || isSending) return
        val msg = text.trim()
        inputText = ""
        isSending = true

        if (onSendMessage != null) {
            onSendMessage(msg) { result ->
                isSending = false
            }
        } else {
            coroutineScope.launch {
                val result = soulRepository.sendMessage(sessionId, persona, msg)
                isSending = false
            }
        }
    }

    fun handleRetry() {
        if (isSending) return
        isSending = true
        if (onRetryMessage != null) {
            onRetryMessage()
            isSending = false
        } else {
            coroutineScope.launch {
                soulRepository.retryLastMessage(sessionId, currentPersona)
                isSending = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Custom Background Image if provided (supports BLOB & Uri with configurable opacity)
        val bgModel: Any? = currentPersona.customChatBgBlob ?: currentPersona.customChatBgUri?.ifBlank { null }
        val bgOpacity = currentPersona.customChatBgOpacity.coerceIn(0.0f, 1.0f)

        if (bgModel != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(bgModel)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF101014).copy(alpha = (1.0f - bgOpacity).coerceIn(0.0f, 0.95f)))
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF121216))
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(top = 8.dp),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onEditPersona(currentPersona) }
                        ) {
                            AvatarView(
                                name = currentPersona.name,
                                avatarStyle = currentPersona.avatarStyle,
                                avatarSeed = currentPersona.avatarSeed,
                                avatarUri = currentPersona.avatarUri,
                                avatarBlob = currentPersona.avatarBlob,
                                size = 42.dp
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = currentPersona.name,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = currentPersona.relationship,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = moodStatus,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    modifier = Modifier.clickable { showEmotionDialog = true }
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("chat_back_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showInviteSheet = true },
                            modifier = Modifier.testTag("invite_persona_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.GroupAdd,
                                contentDescription = "Invite Persona",
                                tint = Color(0xFFA3E635)
                            )
                        }

                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "More",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Invite Persona to Chat") },
                                    leadingIcon = { Icon(Icons.Default.GroupAdd, contentDescription = null, tint = Color(0xFFA3E635)) },
                                    onClick = {
                                        menuExpanded = false
                                        showInviteSheet = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Emotional Attachment") },
                                    leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        menuExpanded = false
                                        showEmotionDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Memory Recap") },
                                    leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        menuExpanded = false
                                        onOpenMemoryRecap(currentPersona)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Edit Persona") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onEditPersona(currentPersona)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear Chat History", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        menuExpanded = false
                                        coroutineScope.launch {
                                            soulRepository.clearChatHistory(sessionId)
                                        }
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Messages List
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
                ) {
                    if (messages.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    AvatarView(
                                        name = currentPersona.name,
                                        avatarStyle = currentPersona.avatarStyle,
                                        avatarSeed = currentPersona.avatarSeed,
                                        avatarUri = currentPersona.avatarUri,
                                        avatarBlob = currentPersona.avatarBlob,
                                        size = 72.dp
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "You are now chatting with ${persona.name}",
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = persona.bio.ifBlank { "Say hello to start your conversation!" },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )
                                }
                            }
                        }
                    }

                    items(messages, key = { it.messageId }) { msg ->
                        ChatMessageBubble(
                            message = msg,
                            persona = currentPersona,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(msg.content))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            onRetry = { handleRetry() }
                        )
                    }

                    if (isSending) {
                        item {
                            TypingIndicatorBubble(persona = currentPersona)
                        }
                    }
                }

                if (messages.lastOrNull()?.isError == true && !isSending) {
                    Surface(
                        color = Color(0xFF3F1717),
                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFFCA5A5),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "AI response failed. Tap retry to try again.",
                                    fontSize = 12.sp,
                                    color = Color(0xFFFCA5A5)
                                )
                            }
                            Button(
                                onClick = { handleRetry() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFEF4444),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Retry", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Quick Suggestions Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickPrompts.take(2).forEach { prompt ->
                        SuggestionChip(
                            onClick = { handleSend(prompt) },
                            label = { Text(prompt, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface) },
                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )
                    }
                }

                // Input Bar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .windowInsetsPadding(WindowInsets.navigationBars),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Message ${persona.name}...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_input_field"),
                            shape = RoundedCornerShape(24.dp),
                            colors = customTextFieldColors(),
                            maxLines = 4
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = { handleSend(inputText) },
                            enabled = inputText.isNotBlank() && !isSending,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (inputText.isNotBlank() && !isSending)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .testTag("send_message_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (inputText.isNotBlank() && !isSending) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (showEmotionDialog) {
            AlertDialog(
                onDismissRequest = { showEmotionDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI Emotional Attachment", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "${currentPersona.name}'s emotional state is updated dynamically by the AI as your relationship grows:",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (emotions.isEmpty()) {
                            Text("Emotional State: Neutral (50%)", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        } else {
                            emotions.forEach { emotionItem ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = emotionItem.emotion,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${emotionItem.percentage}%",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { (emotionItem.percentage / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showEmotionDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        if (showInviteSheet) {
            val otherPersonas = remember(allPersonas, currentPersona.id) {
                allPersonas.filter { it.id != currentPersona.id }
            }

            ModalBottomSheet(
                onDismissRequest = { showInviteSheet = false },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.GroupAdd,
                            contentDescription = null,
                            tint = Color(0xFFA3E635),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Invite Persona to Dialogue",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Select someone to converse with ${currentPersona.name}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (otherPersonas.isEmpty()) {
                        Text(
                            text = "No other personas available. Create another persona first!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                        ) {
                            items(otherPersonas, key = { it.id }) { pB ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF1E2022),
                                    border = BorderStroke(1.dp, Color(0xFF2C3033)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showInviteSheet = false
                                            onStartDualChat?.invoke(currentPersona, pB)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AvatarView(
                                            name = pB.name,
                                            avatarStyle = pB.avatarStyle,
                                            avatarSeed = pB.avatarSeed,
                                            avatarUri = pB.avatarUri,
                                            avatarBlob = pB.avatarBlob,
                                            size = 36.dp
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = pB.name,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFF2F4F2),
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "${pB.relationship} • ${pB.gender}",
                                                color = Color.Gray,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Icon(
                                            Icons.AutoMirrored.Filled.Chat,
                                            contentDescription = "Start Dual Chat",
                                            tint = Color(0xFFA3E635),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun TypingIndicatorBubble(
    persona: PersonaEntity
) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing_bubble")

    val auraAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraAlpha"
    )

    val dot1Scale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    val dot2Scale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, delayMillis = 150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    val dot3Scale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, delayMillis = 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1Alpha"
    )
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, delayMillis = 150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2Alpha"
    )
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, delayMillis = 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3Alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFA3E635).copy(alpha = auraAlpha * 0.35f))
            )
            AvatarView(
                name = persona.name,
                avatarStyle = persona.avatarStyle,
                avatarSeed = persona.avatarSeed,
                avatarUri = persona.avatarUri,
                avatarBlob = persona.avatarBlob,
                size = 32.dp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = 4.dp,
                bottomEnd = 18.dp
            ),
            color = Color(0xFF1E2022),
            border = BorderStroke(1.dp, Color(0xFF2C3033)),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Row(
                modifier = Modifier.height(IntrinsicSize.Max),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(3.dp)
                        .background(Color(0xFFA3E635))
                )

                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .graphicsLayer {
                                    scaleX = dot1Scale
                                    scaleY = dot1Scale
                                    alpha = dot1Alpha
                                }
                                .background(Color(0xFFA3E635), CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .graphicsLayer {
                                    scaleX = dot2Scale
                                    scaleY = dot2Scale
                                    alpha = dot2Alpha
                                }
                                .background(Color(0xFFA3E635), CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .graphicsLayer {
                                    scaleX = dot3Scale
                                    scaleY = dot3Scale
                                    alpha = dot3Alpha
                                }
                                .background(Color(0xFFA3E635), CircleShape)
                        )
                    }

                    Text(
                        text = "${persona.name} is thinking...",
                        color = Color(0xFFF2F4F2),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

fun buildFormattedChatMessage(
    content: String,
    limeColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        val regex = Regex("""\*\*(.*?)\*\*|\*(.*?)\*""")
        var lastIndex = 0

        for (match in regex.findAll(content)) {
            val range = match.range
            if (range.first > lastIndex) {
                val normalText = content.substring(lastIndex, range.first).replace("*", "")
                append(normalText)
            }

            val doubleText = match.groupValues[1]
            val singleText = match.groupValues[2]

            if (doubleText.isNotEmpty()) {
                val cleanText = doubleText.replace("*", "")
                pushStyle(SpanStyle(color = limeColor, fontWeight = FontWeight.Bold))
                append(cleanText)
                pop()
            } else if (singleText.isNotEmpty()) {
                val cleanText = singleText.replace("*", "")
                pushStyle(SpanStyle(color = limeColor, fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold))
                append(cleanText)
                pop()
            }

            lastIndex = range.last + 1
        }

        if (lastIndex < content.length) {
            val remainingText = content.substring(lastIndex).replace("*", "")
            append(remainingText)
        }
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatMessageEntity,
    persona: PersonaEntity,
    onCopy: () -> Unit,
    onRetry: (() -> Unit)? = null
) {
    val isUser = message.role == "user"
    val timeFormatted = remember(message.timestamp) {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        sdf.format(Date(message.timestamp))
    }

    if (isUser) {
        val formattedUserContent = remember(message.content) {
            buildFormattedChatMessage(message.content, limeColor = Color(0xFF3F6212))
        }

        // User Message Styling (Soft Lime Pill, Dark Text, Bottom Right Timestamp)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 4.dp
                ),
                color = Color(0xFFD9F99D), // Soft lime pill
                modifier = Modifier.widthIn(max = 290.dp)
            ) {
                Text(
                    text = formattedUserContent,
                    color = Color(0xFF1A2E05), // Dark green-black text on soft lime
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = timeFormatted,
                color = Color.Gray.copy(alpha = 0.8f),
                fontSize = 11.sp,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
    } else {
        val formattedAiContent = remember(message.content) {
            buildFormattedChatMessage(message.content, limeColor = Color(0xFFA3E635))
        }

        // Persona AI Message Styling (Dark card with soft lime left accent border, avatar on left)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Small Persona Avatar on left
            AvatarView(
                name = persona.name,
                avatarStyle = persona.avatarStyle,
                avatarSeed = persona.avatarSeed,
                avatarUri = persona.avatarUri,
                avatarBlob = persona.avatarBlob,
                size = 32.dp
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    // Dark Bubble with Lime Accent Border on left edge
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = 4.dp,
                            bottomEnd = 18.dp
                        ),
                        color = if (message.isError) MaterialTheme.colorScheme.errorContainer else Color(0xFF1E2022),
                        border = BorderStroke(
                            1.dp,
                            if (message.isError) MaterialTheme.colorScheme.error else Color(0xFF2C3033)
                        ),
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Row(
                            modifier = Modifier.height(IntrinsicSize.Max),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Lime Accent Stripe on left edge
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(3.dp)
                                    .background(
                                        if (message.isError) MaterialTheme.colorScheme.error else Color(0xFFA3E635)
                                    )
                            )

                            Text(
                                text = formattedAiContent,
                                color = if (message.isError) MaterialTheme.colorScheme.onErrorContainer else Color(0xFFF2F4F2),
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                            )
                        }
                    }
                }

                if (message.isError && onRetry != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Retry Response", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text(
                        text = timeFormatted,
                        color = Color.Gray.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy message",
                        tint = Color.Gray.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(12.dp)
                            .clickable { onCopy() }
                    )
                }
            }
        }
    }
}
