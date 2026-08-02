package com.example.ui.spaces.personas

import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.spaces.AppearanceFieldsDto
import com.example.data.spaces.model.AppearanceModel
import com.example.data.spaces.model.SpaceModel
import com.example.data.spaces.model.SpacePersonaModel
import com.example.data.spaces.model.UserCharacterModel
import com.example.ui.theme.customTextFieldColors
import com.example.util.AgeUtil
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditSpacePersonaScreen(
    space: SpaceModel,
    existingPersona: SpacePersonaModel?,
    existingUserCharacter: UserCharacterModel?,
    otherPersonas: List<SpacePersonaModel>,
    isUserCharacterMode: Boolean,
    onBack: () -> Unit,
    onSavePersona: (SpacePersonaModel) -> Unit,
    onSaveUserCharacter: (UserCharacterModel) -> Unit,
    onAnalyzePhoto: suspend (imageBase64: String, mimeType: String) -> Result<AppearanceFieldsDto>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var name by remember { mutableStateOf(existingPersona?.name ?: existingUserCharacter?.name ?: "") }
    var dob by remember { mutableStateOf(existingPersona?.dob ?: existingUserCharacter?.dob ?: "") }
    var background by remember { mutableStateOf(existingPersona?.background ?: existingUserCharacter?.background ?: "") }

    var relationshipToUser by remember { mutableStateOf(existingPersona?.relationshipToUser ?: "") }
    var bio by remember { mutableStateOf(existingPersona?.bio ?: "") }
    var mood by remember { mutableFloatStateOf((existingPersona?.mood ?: 0).toFloat()) }
    var aggressiveness by remember { mutableFloatStateOf((existingPersona?.aggressiveness ?: 0).toFloat()) }
    val relationshipTones = remember {
        mutableStateMapOf<String, String>().apply {
            existingPersona?.relationshipsToOtherPersonas?.let { putAll(it) }
        }
    }

    val initialAppearance = existingPersona?.appearance ?: existingUserCharacter?.appearance ?: AppearanceModel()
    var hairColor by remember { mutableStateOf(initialAppearance.hairColor) }
    var hairStyle by remember { mutableStateOf(initialAppearance.hairStyle) }
    var eyeColor by remember { mutableStateOf(initialAppearance.eyeColor) }
    var skinTone by remember { mutableStateOf(initialAppearance.skinTone) }
    var build by remember { mutableStateOf(initialAppearance.build) }
    var height by remember { mutableStateOf(initialAppearance.height) }
    var extraFeatures by remember { mutableStateOf(initialAppearance.extraFeatures) }

    var isAnalyzingPhoto by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isAnalyzingPhoto = true
            photoError = null
            try {
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                    val output = ByteArrayOutputStream()
                    input.copyTo(output)
                    output.toByteArray()
                }
                if (bytes == null) {
                    photoError = "Couldn't read that photo."
                } else {
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val result = onAnalyzePhoto(base64, mimeType)
                    result.onSuccess { fields ->
                        if (fields.hairColor.isNotBlank()) hairColor = fields.hairColor
                        if (fields.hairStyle.isNotBlank()) hairStyle = fields.hairStyle
                        if (fields.eyeColor.isNotBlank()) eyeColor = fields.eyeColor
                        if (fields.skinTone.isNotBlank()) skinTone = fields.skinTone
                        if (fields.build.isNotBlank()) build = fields.build
                        if (fields.height.isNotBlank()) height = fields.height
                        if (fields.extraFeatures.isNotBlank()) extraFeatures = fields.extraFeatures
                    }.onFailure {
                        photoError = it.localizedMessage ?: "Photo analysis failed."
                    }
                }
            } finally {
                isAnalyzingPhoto = false
            }
        }
    }

    fun handleSave() {
        if (isUserCharacterMode) {
            onSaveUserCharacter(
                UserCharacterModel(
                    name = name.trim(),
                    dob = dob.trim(),
                    background = background.trim(),
                    appearance = AppearanceModel(hairColor, hairStyle, eyeColor, skinTone, build, height, extraFeatures)
                )
            )
        } else {
            onSavePersona(
                SpacePersonaModel(
                    id = existingPersona?.id ?: "",
                    spaceId = space.id,
                    name = name.trim(),
                    dob = dob.trim(),
                    gender = existingPersona?.gender ?: "",
                    relationshipToUser = relationshipToUser.trim(),
                    bio = bio.trim(),
                    background = background.trim(),
                    mood = mood.toInt(),
                    aggressiveness = aggressiveness.toInt(),
                    appearance = AppearanceModel(hairColor, hairStyle, eyeColor, skinTone, build, height, extraFeatures),
                    relationshipsToOtherPersonas = relationshipTones.filterValues { it.isNotBlank() },
                    avatarStyle = existingPersona?.avatarStyle ?: "Avataaars (Modern)",
                    avatarSeed = existingPersona?.avatarSeed ?: name
                )
            )
        }
    }

    val age = AgeUtil.computeAge(dob, space.simDate)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 12.dp),
                title = {
                    Column {
                        Text(
                            text = if (isUserCharacterMode) "Your Character" else if (existingPersona == null) "New Persona" else "Edit Persona",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 20.sp
                        )
                        Text(
                            text = if (isUserCharacterMode) "How you appear in this story" else "Details shape how they act and speak",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
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
                .padding(20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            LabeledField("Name", name, { name = it }, placeholder = if (isUserCharacterMode) "Your name" else "Persona name")

            Column {
                LabeledField("Date of birth", dob, { dob = it }, placeholder = "YYYY-MM-DD")
                Text(
                    text = when {
                        age != null -> "Age $age as of the story's current date${if (space.simDate.isNotBlank()) " (${space.simDate})" else ""}"
                        space.simDate.isBlank() -> "Story has no fixed calendar yet -- age not shown"
                        dob.isBlank() -> "Add a birth date to show age"
                        else -> "Couldn't parse that date -- use YYYY-MM-DD"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            LabeledField(
                label = "Background story",
                value = background,
                onValueChange = { background = it },
                placeholder = "Their history -- where they came from, what shaped them",
                minLines = 3
            )

            if (!isUserCharacterMode) {
                LabeledField("Relationship to you", relationshipToUser, { relationshipToUser = it }, placeholder = "e.g. Investor, Co-founder")
                LabeledField("Bio", bio, { bio = it }, placeholder = "A short description")
            }

            // Appearance
            Text(
                text = "Appearance",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column {
                    OutlinedButton(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text("Upload photo to auto-fill", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (isAnalyzingPhoto) {
                        val transition = rememberInfiniteTransition(label = "analyzing")
                        val alpha by transition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(700, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "analyzing_alpha"
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Analyzing photo…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (photoError != null) {
                        Text(photoError ?: "", fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            FlowRowFields {
                LabeledField("Hair color", hairColor, { hairColor = it }, placeholder = "e.g. Auburn", modifier = Modifier.weight(1f))
                LabeledField("Hair style", hairStyle, { hairStyle = it }, placeholder = "e.g. Shoulder length", modifier = Modifier.weight(1f))
            }
            FlowRowFields {
                LabeledField("Eye color", eyeColor, { eyeColor = it }, placeholder = "e.g. Green", modifier = Modifier.weight(1f))
                LabeledField("Skin tone", skinTone, { skinTone = it }, placeholder = "e.g. Olive", modifier = Modifier.weight(1f))
            }
            FlowRowFields {
                LabeledField("Build", build, { build = it }, placeholder = "e.g. Athletic", modifier = Modifier.weight(1f))
                LabeledField("Height", height, { height = it }, placeholder = "e.g. 5'8\"", modifier = Modifier.weight(1f))
            }
            LabeledField("Additional features", extraFeatures, { extraFeatures = it }, placeholder = "Scars, tattoos, accessories, style quirks")

            if (!isUserCharacterMode) {
                Column {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Mood", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(moodLabel(mood.toInt()), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Slider(value = mood, onValueChange = { mood = it }, valueRange = -100f..100f, modifier = Modifier.fillMaxWidth())
                    Text("Drifts over time based on how the story treats them.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Column {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Aggressiveness", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(aggroLabel(aggressiveness.toInt()), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Slider(value = aggressiveness, onValueChange = { aggressiveness = it }, valueRange = 0f..100f, modifier = Modifier.fillMaxWidth())
                    Text("Colors how sharply they push back in conversation.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (otherPersonas.isNotEmpty()) {
                    Text(
                        text = "Relationships",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    otherPersonas.forEach { other ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(other.name, fontSize = 13.sp, modifier = Modifier.weight(0.4f))
                            OutlinedTextField(
                                value = relationshipTones[other.id] ?: "",
                                onValueChange = { relationshipTones[other.id] = it },
                                placeholder = { Text("e.g. Friendly, Cautious", fontSize = 12.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = customTextFieldColors(),
                                modifier = Modifier.weight(0.6f)
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { handleSave() },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = if (isUserCharacterMode) "Save" else if (existingPersona == null) "Create Persona" else "Save Changes",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    minLines: Int = 1,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            minLines = minLines,
            shape = RoundedCornerShape(16.dp),
            colors = customTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun FlowRowFields(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        content()
    }
}

private fun moodLabel(value: Int) = when {
    value < -34 -> "Tense"
    value < 34 -> "Neutral"
    else -> "Calm"
}

private fun aggroLabel(value: Int) = when {
    value < 34 -> "Gentle"
    value < 67 -> "Assertive"
    else -> "Combative"
}
