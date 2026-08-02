package com.example.ui.personas

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PersonaEntity
import com.example.data.model.UserConfigEntity
import com.example.data.repository.SoulRepository
import com.example.ui.components.AvatarView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonasScreen(
    personas: List<PersonaEntity>,
    userConfig: UserConfigEntity?,
    onSelectPersonaForChat: (PersonaEntity) -> Unit,
    onOpenMemoryRecap: (PersonaEntity) -> Unit,
    onCreateNewPersona: () -> Unit,
    onEditPersona: (PersonaEntity) -> Unit,
    onDeletePersona: (PersonaEntity) -> Unit,
    onOpenSettings: () -> Unit,
    onStartDualChat: ((PersonaEntity, PersonaEntity) -> Unit)? = null,
    soulRepository: SoulRepository
) {
    var showDualDialog by remember { mutableStateOf(false) }
    var selectedPersonaA by remember { mutableStateOf<PersonaEntity?>(null) }
    var selectedPersonaB by remember { mutableStateOf<PersonaEntity?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 12.dp),
                title = {
                    Column {
                        Text(
                            text = "Personas",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 24.sp
                        )
                        Text(
                            text = "Choose someone to talk to",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                },
                actions = {
                    if (personas.size >= 2) {
                        IconButton(
                            onClick = {
                                selectedPersonaA = personas.getOrNull(0)
                                selectedPersonaB = personas.getOrNull(1)
                                showDualDialog = true
                            },
                            modifier = Modifier.testTag("persona_lounge_button")
                        ) {
                            Icon(
                                Icons.Default.Forum,
                                contentDescription = "Persona Lounge",
                                tint = Color(0xFFA3E635)
                            )
                        }
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("open_settings_button")
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateNewPersona,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .navigationBarsPadding()
                    .size(56.dp)
                    .testTag("create_persona_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Persona", modifier = Modifier.size(28.dp))
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            if (personas.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.SentimentDissatisfied,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No AI Personas found",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Tap '+' button to build a custom companion!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                if (personas.size >= 2) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1E2022),
                        border = BorderStroke(1.dp, Color(0xFFA3E635).copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clickable {
                                selectedPersonaA = personas.getOrNull(0)
                                selectedPersonaB = personas.getOrNull(1)
                                showDualDialog = true
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFA3E635).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Groups,
                                    contentDescription = null,
                                    tint = Color(0xFFA3E635),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Inter-Persona Chat Lounge",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Watch two personas converse in real time (Spectator Mode).",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    selectedPersonaA = personas.getOrNull(0)
                                    selectedPersonaB = personas.getOrNull(1)
                                    showDualDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFA3E635),
                                    contentColor = Color(0xFF1A2E05)
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Start", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(personas, key = { it.id }) { persona ->
                        PersonaListItem(
                            persona = persona,
                            onChatClick = { onSelectPersonaForChat(persona) },
                            onMemoryClick = { onOpenMemoryRecap(persona) },
                            onEditClick = { onEditPersona(persona) },
                            onDeleteClick = { onDeletePersona(persona) }
                        )
                    }
                }
            }
        }

        if (showDualDialog && personas.size >= 2) {
            AlertDialog(
                onDismissRequest = { showDualDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Forum, contentDescription = null, tint = Color(0xFFA3E635))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Persona Lounge Dialogue", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Select two personas to let them converse with each other while you observe:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text("First Persona:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        LazyColumn(modifier = Modifier.height(90.dp)) {
                            items(personas) { p ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedPersonaA = p }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = selectedPersonaA?.id == p.id, onClick = { selectedPersonaA = p })
                                    Text(p.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        Text("Second Persona:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        LazyColumn(modifier = Modifier.height(90.dp)) {
                            items(personas.filter { it.id != selectedPersonaA?.id }) { p ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedPersonaB = p }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = selectedPersonaB?.id == p.id, onClick = { selectedPersonaB = p })
                                    Text(p.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val pA = selectedPersonaA
                            val pB = selectedPersonaB
                            if (pA != null && pB != null && pA.id != pB.id) {
                                showDualDialog = false
                                onStartDualChat?.invoke(pA, pB)
                            }
                        },
                        enabled = selectedPersonaA != null && selectedPersonaB != null && selectedPersonaA?.id != selectedPersonaB?.id
                    ) {
                        Text("Start Dialogue")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDualDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun PersonaListItem(
    persona: PersonaEntity,
    onChatClick: () -> Unit,
    onMemoryClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .clickable { onChatClick() }
            .testTag("persona_card_${persona.name}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarView(
                name = persona.name,
                avatarStyle = persona.avatarStyle,
                avatarSeed = persona.avatarSeed,
                avatarUri = persona.avatarUri,
                avatarBlob = persona.avatarBlob,
                size = 52.dp
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = persona.name,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (persona.relationship.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = persona.relationship,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = persona.bio.ifBlank { "Tap to start chatting with ${persona.name}" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = onMemoryClick,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("memory_recap_icon_${persona.name}")
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = "Memory Recap",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onEditClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit Persona",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
