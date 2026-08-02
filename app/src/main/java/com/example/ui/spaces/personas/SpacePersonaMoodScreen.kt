package com.example.ui.spaces.personas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.spaces.model.RelationshipEmotions
import com.example.data.spaces.model.SpacePersonaModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpacePersonaMoodScreen(
    persona: SpacePersonaModel,
    otherPersonas: List<SpacePersonaModel>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding().padding(top = 12.dp),
                title = {
                    Column {
                        Text("Mood & Feelings", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, fontSize = 20.sp)
                        Text(persona.name, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SectionLabel("Baseline")
            MeterRow("Mood", moodLabel(persona.mood), fraction = (persona.mood + 100) / 200f)
            MeterRow("Aggressiveness", aggroLabel(persona.aggressiveness), fraction = persona.aggressiveness / 100f)

            SectionLabel("Feelings toward you")
            EmotionMeters(persona.emotionsTowardUser)

            if (otherPersonas.isNotEmpty()) {
                SectionLabel("Feelings toward others")
                otherPersonas.forEach { other ->
                    val emotions = persona.emotionsTowardPersonas[other.id]
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(other.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        EmotionMeters(emotions ?: RelationshipEmotions())
                    }
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
private fun EmotionMeters(emotions: RelationshipEmotions) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MeterRow("Affection", "${emotions.affection}", fraction = emotions.affection / 100f)
        MeterRow("Love", "${emotions.love}", fraction = emotions.love / 100f)
        MeterRow("Lust", "${emotions.lust}", fraction = emotions.lust / 100f)
        MeterRow("Trust", "${emotions.trust}", fraction = emotions.trust / 100f)
    }
}

@Composable
private fun MeterRow(label: String, valueLabel: String, fraction: Float) {
    Column {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
            Text(valueLabel, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round
        )
    }
}

private fun moodLabel(value: Int) = when {
    value < -34 -> "Tense ($value)"
    value < 34 -> "Neutral ($value)"
    else -> "Calm ($value)"
}

private fun aggroLabel(value: Int) = when {
    value < 34 -> "Gentle ($value)"
    value < 67 -> "Assertive ($value)"
    else -> "Combative ($value)"
}
