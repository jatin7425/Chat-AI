package com.example.ui.chat

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatMessageEntity
import com.example.data.model.PersonaEntity
import com.example.data.repository.SoulRepository
import com.example.ui.components.AvatarView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DualPersonaChatScreen(
    personaA: PersonaEntity,
    personaB: PersonaEntity,
    sessionId: String,
    messages: List<ChatMessageEntity>,
    onBack: () -> Unit,
    onNextTurn: ((Result<String>) -> Unit) -> Unit,
    soulRepository: SoulRepository
) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    var isSending by remember { mutableStateOf(false) }
    var isAutoPlay by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Auto-play turns loop
    LaunchedEffect(isAutoPlay, isSending) {
        if (isAutoPlay && !isSending) {
            val lastMsg = messages.lastOrNull()
            if (lastMsg == null || !lastMsg.isError) {
                delay(2500)
                if (isAutoPlay && !isSending) {
                    isSending = true
                    onNextTurn { result ->
                        isSending = false
                        if (result.isFailure) {
                            isAutoPlay = false
                        }
                    }
                }
            } else {
                // Pause auto play if error occurred
                isAutoPlay = false
            }
        }
    }

    fun triggerNextTurn() {
        if (isSending) return
        isSending = true
        onNextTurn { result ->
            isSending = false
        }
    }

    // Determine current speaker for typing indicator
    val lastSpeakerId = messages.lastOrNull { !it.isError }?.personaId
    val currentThinkingPersona = if (lastSpeakerId == personaB.id) personaA else personaB

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 8.dp),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AvatarView(
                                    name = personaA.name,
                                    avatarStyle = personaA.avatarStyle,
                                    avatarSeed = personaA.avatarSeed,
                                    avatarUri = personaA.avatarUri,
                                    avatarBlob = personaA.avatarBlob,
                                    size = 32.dp
                                )
                                Spacer(modifier = Modifier.width(-8.dp))
                                AvatarView(
                                    name = personaB.name,
                                    avatarStyle = personaB.avatarStyle,
                                    avatarSeed = personaB.avatarSeed,
                                    avatarUri = personaB.avatarUri,
                                    avatarBlob = personaB.avatarBlob,
                                    size = 32.dp
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "${personaA.name} & ${personaB.name}",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 16.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFA3E635))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Inter-Persona Lounge • Spectator Mode",
                                    color = Color(0xFFA3E635),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Clear Chat",
                            tint = Color.Gray
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            Surface(
                color = Color(0xFF141517),
                border = BorderStroke(1.dp, Color(0xFF26282B)),
                modifier = Modifier.navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    // Auto Play & Next Turn Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = isAutoPlay,
                                onCheckedChange = { isAutoPlay = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF1A2E05),
                                    checkedTrackColor = Color(0xFFA3E635)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isAutoPlay) "Auto Dialogue: ON" else "Auto Dialogue: OFF",
                                color = if (isAutoPlay) Color(0xFFA3E635) else Color.LightGray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = { triggerNextTurn() },
                            enabled = !isSending,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFA3E635),
                                contentColor = Color(0xFF1A2E05)
                            ),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSending) "Thinking..." else "Next Turn",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // User Interference Disabled Badge
                    Surface(
                        color = Color(0xFF1E2022),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "User interference disabled — sit back and observe their dialogue.",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Spectator Banner
            Surface(
                color = Color(0xFF181B1E),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = null,
                        tint = Color(0xFFA3E635),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Persona AI dialogue live stream. ${personaA.name} and ${personaB.name} are interacting in real time.",
                        color = Color(0xFFD1D5DB),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            // Message List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                Icon(
                                    imageVector = Icons.Default.Forum,
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Ready to start persona dialogue!",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap 'Next Turn' or toggle 'Auto Dialogue' below.",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                items(messages, key = { it.messageId }) { msg ->
                    val isSpeakerA = msg.personaId == personaA.id
                    val speakerPersona = if (isSpeakerA) personaA else personaB

                    DualChatMessageBubble(
                        message = msg,
                        speakerPersona = speakerPersona,
                        isPersonaA = isSpeakerA,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(msg.content))
                        },
                        onRetry = { triggerNextTurn() }
                    )
                }

                if (isSending) {
                    item {
                        TypingIndicatorBubble(persona = currentThinkingPersona)
                    }
                }
            }

            // Error Retry Bottom Bar
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
                                text = "Turn failed to generate. Tap retry to try again.",
                                fontSize = 12.sp,
                                color = Color(0xFFFCA5A5)
                            )
                        }
                        Button(
                            onClick = { triggerNextTurn() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEF4444),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry Turn", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Persona Chat?") },
            text = { Text("This will reset the conversation history between ${personaA.name} and ${personaB.name}.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        coroutineScope.launch {
                            soulRepository.clearChatHistory(sessionId)
                        }
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DualChatMessageBubble(
    message: ChatMessageEntity,
    speakerPersona: PersonaEntity,
    isPersonaA: Boolean,
    onCopy: () -> Unit,
    onRetry: () -> Unit
) {
    val themeAccentColor = if (isPersonaA) Color(0xFFA3E635) else Color(0xFF38BDF8)
    val formattedContent = remember(message.content) {
        buildFormattedChatMessage(message.content, limeColor = themeAccentColor)
    }

    val timeFormatted = remember(message.timestamp) {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        sdf.format(Date(message.timestamp))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isPersonaA) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.Top
    ) {
        if (isPersonaA) {
            AvatarView(
                name = speakerPersona.name,
                avatarStyle = speakerPersona.avatarStyle,
                avatarSeed = speakerPersona.avatarSeed,
                avatarUri = speakerPersona.avatarUri,
                avatarBlob = speakerPersona.avatarBlob,
                size = 34.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isPersonaA) Alignment.Start else Alignment.End,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            // Speaker Name
            Text(
                text = speakerPersona.name,
                color = themeAccentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )

            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isPersonaA) 4.dp else 16.dp,
                    bottomEnd = if (isPersonaA) 16.dp else 4.dp
                ),
                color = if (message.isError) MaterialTheme.colorScheme.errorContainer else Color(0xFF1E2022),
                border = BorderStroke(
                    1.dp,
                    if (message.isError) MaterialTheme.colorScheme.error else Color(0xFF2C3033)
                )
            ) {
                Row(
                    modifier = Modifier.height(IntrinsicSize.Max),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isPersonaA) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(3.dp)
                                .background(if (message.isError) MaterialTheme.colorScheme.error else themeAccentColor)
                        )
                    }

                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            text = formattedContent,
                            color = if (message.isError) MaterialTheme.colorScheme.onErrorContainer else Color(0xFFF2F4F2),
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )

                        if (message.isError) {
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
                                Text(text = "Retry Turn", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (!isPersonaA) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(3.dp)
                                .background(if (message.isError) MaterialTheme.colorScheme.error else themeAccentColor)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(
                    text = timeFormatted,
                    color = Color.Gray.copy(alpha = 0.8f),
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy message",
                    tint = Color.Gray.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(11.dp)
                        .clickable { onCopy() }
                )
            }
        }

        if (!isPersonaA) {
            Spacer(modifier = Modifier.width(8.dp))
            AvatarView(
                name = speakerPersona.name,
                avatarStyle = speakerPersona.avatarStyle,
                avatarSeed = speakerPersona.avatarSeed,
                avatarUri = speakerPersona.avatarUri,
                avatarBlob = speakerPersona.avatarBlob,
                size = 34.dp
            )
        }
    }
}
