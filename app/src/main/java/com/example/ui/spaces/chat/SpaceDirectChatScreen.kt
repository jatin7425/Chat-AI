package com.example.ui.spaces.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.spaces.model.DirectChatMessageModel
import com.example.data.spaces.model.NeedsInputModel
import com.example.data.spaces.model.SpaceModel
import com.example.data.spaces.model.SpacePersonaModel
import com.example.ui.components.AvatarView
import com.example.ui.components.ChatMessageText
import com.example.ui.theme.customTextFieldColors
import com.example.util.dashedBorder

private sealed class TimelineItem(val createdAt: Long) {
    class Message(val data: DirectChatMessageModel) : TimelineItem(data.createdAt)
    class NeedsInputCard(val data: NeedsInputModel) : TimelineItem(data.createdAt)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceDirectChatScreen(
    space: SpaceModel,
    persona: SpacePersonaModel,
    messages: List<DirectChatMessageModel>,
    pendingNeedsInput: List<NeedsInputModel>,
    isSending: Boolean,
    sendError: String?,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onRetry: () -> Unit,
    onCreatePersonaForNeedsInput: (targetPersonaName: String) -> Unit,
    onDismissNeedsInput: (requestId: String) -> Unit,
    onViewProfile: () -> Unit,
    onEditPersona: () -> Unit,
    onViewMood: () -> Unit,
    modifier: Modifier = Modifier
) {
    var draft by remember { mutableStateOf("") }
    var bannerDismissed by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val timeline = remember(messages, pendingNeedsInput) {
        (messages.map { TimelineItem.Message(it) } + pendingNeedsInput.map { TimelineItem.NeedsInputCard(it) })
            .sortedBy { it.createdAt }
    }

    LaunchedEffect(timeline.size) {
        if (timeline.isNotEmpty()) listState.animateScrollToItem(timeline.size - 1)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 12.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarView(name = persona.name, avatarStyle = persona.avatarStyle, avatarSeed = persona.avatarSeed, avatarUri = persona.avatarImageUrl.ifBlank { null }, size = 36.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(persona.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (persona.relationshipToUser.isNotBlank()) {
                                Text(persona.relationshipToUser, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text("View Profile") }, onClick = { menuExpanded = false; onViewProfile() })
                            DropdownMenuItem(text = { Text("Edit Persona") }, onClick = { menuExpanded = false; onEditPersona() })
                            DropdownMenuItem(text = { Text("Persona Mood") }, onClick = { menuExpanded = false; onViewMood() })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (persona.chatBackgroundImageUrl.isNotBlank()) {
                AsyncImage(
                    model = persona.chatBackgroundImageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = persona.chatBackgroundOpacity,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
            if (space.simStatus == "running" && !bannerDismissed) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Simulation running — things may happen while you're away",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { bannerDismissed = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(timeline, key = { it.createdAt.toString() + it.hashCode() }) { item ->
                    when (item) {
                        is TimelineItem.Message -> ChatBubble(item.data)
                        is TimelineItem.NeedsInputCard -> NeedsInputCardView(
                            data = item.data,
                            onCreatePersona = { onCreatePersonaForNeedsInput(item.data.targetPersonaName) },
                            onNotAPersona = { onDismissNeedsInput(item.data.id) }
                        )
                    }
                }
            }

            if (sendError != null) {
                val canRetry = messages.lastOrNull()?.role == "user"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = sendError,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (canRetry) {
                        TextButton(onClick = onRetry, enabled = !isSending) {
                            Text("Retry", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("Message ${persona.name}") },
                    shape = RoundedCornerShape(20.dp),
                    colors = customTextFieldColors(),
                    modifier = Modifier.weight(1f),
                    enabled = !isSending
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (draft.isNotBlank() && !isSending) {
                            onSendMessage(draft.trim())
                            draft = ""
                        }
                    },
                    enabled = !isSending,
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                ) {
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: DirectChatMessageModel) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            ChatMessageText(
                text = message.text,
                color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun NeedsInputCardView(data: NeedsInputModel, onCreatePersona: () -> Unit, onNotAPersona: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .dashedBorder(color = MaterialTheme.colorScheme.error, cornerRadius = 16.dp)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Needs your input", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Text(data.question, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onCreatePersona,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("Create ${data.targetPersonaName} persona", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onNotAPersona,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("Not a persona", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
