package com.example.ui.spaces.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.spaces.model.ActivityLogEntryModel
import com.example.data.spaces.model.SpaceModel
import com.example.ui.spaces.components.PillTone
import com.example.ui.spaces.components.StatusPill
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A space-wide, descending-by-time feed of every persona's activity -- backed by a live Firestore
 * listener (see SpacesRepository.observeSpaceActivityLog), so it updates in real time whenever
 * the simulation is running and new entries stream in, and just shows saved history otherwise.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceActivityLogScreen(
    space: SpaceModel,
    activityLog: List<ActivityLogEntryModel>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRunning = space.simStatus == "running"

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding().padding(top = 12.dp),
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Activity Log", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            if (isRunning) StatusPill("● Live", PillTone.PRIMARY) else StatusPill("Paused", PillTone.MUTED)
                        }
                        Text(space.name, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                },
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
        if (activityLog.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.SentimentDissatisfied, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Nothing's happened yet", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (isRunning) "Give it a moment -- personas are just getting started" else "Start the simulation or chat with someone to get things going",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).navigationBarsPadding().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
            ) {
                items(activityLog, key = { it.id }) { entry -> ActivityLogRow(entry) }
            }
        }
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
            Text(
                buildString {
                    if (entry.personaName.isNotBlank()) {
                        append(entry.personaName)
                        append(": ")
                    }
                    append(entry.description)
                },
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                formatActivityTimestamp(entry.createdAt) + if (entry.placeName.isNotBlank()) " • ${entry.placeName}" else "",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatActivityTimestamp(millis: Long): String {
    if (millis <= 0L) return ""
    return SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(millis))
}
