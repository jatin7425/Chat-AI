package com.example.ui.spaces.personas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.spaces.model.ActivityLogEntryModel
import com.example.data.spaces.model.PlaceModel
import com.example.data.spaces.model.SpaceModel
import com.example.data.spaces.model.SpacePersonaModel
import com.example.data.spaces.model.StoryFeedTaskModel
import com.example.data.spaces.model.UserCharacterModel
import com.example.ui.components.AvatarView
import com.example.ui.theme.customTextFieldColors
import com.example.util.AgeUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpacePersonaProfileScreen(
    space: SpaceModel,
    persona: SpacePersonaModel,
    otherPersonas: List<SpacePersonaModel>,
    userCharacter: UserCharacterModel?,
    places: List<PlaceModel>,
    activityLog: List<ActivityLogEntryModel>,
    todoTasks: List<StoryFeedTaskModel> = emptyList(),
    onBack: () -> Unit,
    onMoveToPlace: (placeId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val age = AgeUtil.computeAge(persona.dob, space.simDate)
    var placePickerExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding().padding(top = 12.dp),
                title = { Text("Profile", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarView(
                    name = persona.name,
                    avatarStyle = persona.avatarStyle,
                    avatarSeed = persona.avatarSeed,
                    avatarUri = persona.avatarImageUrl.ifBlank { null },
                    size = 76.dp
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(persona.name, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                    if (persona.relationshipToUser.isNotBlank()) {
                        Text(persona.relationshipToUser, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (age != null) {
                        Text("Age $age", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }

            // Location
            SectionLabel("Location")
            ExposedDropdownMenuBox(expanded = placePickerExpanded, onExpandedChange = { placePickerExpanded = it }) {
                OutlinedTextField(
                    value = persona.currentPlaceName.ifBlank { "Unknown" },
                    onValueChange = {},
                    readOnly = true,
                    leadingIcon = { Icon(Icons.Default.Place, contentDescription = null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = placePickerExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = RoundedCornerShape(12.dp),
                    colors = customTextFieldColors()
                )
                ExposedDropdownMenu(expanded = placePickerExpanded, onDismissRequest = { placePickerExpanded = false }) {
                    if (places.isEmpty()) {
                        DropdownMenuItem(text = { Text("No places yet -- add one from Space Home") }, onClick = { placePickerExpanded = false })
                    }
                    places.forEach { place ->
                        DropdownMenuItem(
                            text = { Text(place.name) },
                            onClick = { placePickerExpanded = false; onMoveToPlace(place.id) }
                        )
                    }
                }
            }
            if (userCharacter?.currentPlaceName?.isNotBlank() == true) {
                Text(
                    "You're currently at ${userCharacter.currentPlaceName}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            // Portfolio
            if (persona.portfolioImageUrls.isNotEmpty()) {
                SectionLabel("Portfolio")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(persona.portfolioImageUrls) { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = "Portfolio photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                }
            }

            // Details
            SectionLabel("Details")
            if (persona.bio.isNotBlank()) DetailRow("Bio", persona.bio)
            if (persona.background.isNotBlank()) DetailRow("Background", persona.background)
            val appearanceParts = listOfNotNull(
                persona.appearance.hairColor.takeIf { it.isNotBlank() }?.let { "Hair: $it" },
                persona.appearance.hairStyle.takeIf { it.isNotBlank() },
                persona.appearance.eyeColor.takeIf { it.isNotBlank() }?.let { "Eyes: $it" },
                persona.appearance.skinTone.takeIf { it.isNotBlank() }?.let { "Skin: $it" },
                persona.appearance.build.takeIf { it.isNotBlank() },
                persona.appearance.height.takeIf { it.isNotBlank() },
                persona.appearance.extraFeatures.takeIf { it.isNotBlank() }
            )
            if (appearanceParts.isNotEmpty()) DetailRow("Appearance", appearanceParts.joinToString(" • "))

            // Relationships
            SectionLabel("Relationships")
            DetailRow("With you", persona.relationshipToUser.ifBlank { "Not set" })
            if (otherPersonas.isNotEmpty()) {
                otherPersonas.forEach { other ->
                    val tone = persona.relationshipsToOtherPersonas[other.id]
                    if (!tone.isNullOrBlank()) DetailRow(other.name, tone)
                }
            }

            // To-Do -- what the orchestrator has this persona doing, plus things they're waiting
            // to bring up with you (queued so they don't message you about several things at once).
            if (todoTasks.isNotEmpty() || persona.pendingTopics.isNotEmpty()) {
                SectionLabel("To-Do")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    todoTasks.forEach { task -> TodoRow(task.description, statusLabel(task.status)) }
                    persona.pendingTopics.forEach { topic -> TodoRow("Wants to tell you: $topic", "Queued") }
                }
            }

            if (persona.coreMemories.isNotEmpty()) {
                SectionLabel("Core Memories")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    persona.coreMemories.asReversed().forEach { memory ->
                        Text("• $memory", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            // Activity Log
            SectionLabel("Activity Log")
            if (activityLog.isEmpty()) {
                Text("Nothing's happened yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    activityLog.take(30).forEach { entry -> ActivityLogRow(entry) }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
        Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ActivityLogRow(entry: ActivityLogEntryModel) {
    val icon = when (entry.type) {
        "move" -> Icons.Default.DirectionsWalk
        "mood_shift" -> Icons.Default.Mood
        else -> Icons.Default.Chat
    }
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp).padding(top = 2.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(
                formatTimestamp(entry.createdAt) + if (entry.placeName.isNotBlank()) " • ${entry.placeName}" else "",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatTimestamp(millis: Long): String {
    if (millis <= 0L) return ""
    return SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(millis))
}

@Composable
private fun TodoRow(text: String, status: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(status, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

private fun statusLabel(status: String) = when (status) {
    "in_progress" -> "In Progress"
    "blocked" -> "Blocked"
    "done" -> "Done"
    else -> "Pending"
}
