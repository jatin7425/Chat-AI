package com.example.ui.personas

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EmotionItem
import com.example.data.model.PersonaEntity
import com.example.data.repository.SoulRepository
import com.example.ui.components.AvatarView
import com.example.ui.theme.customTextFieldColors
import com.example.util.ImageUtils

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CreatePersonaSheet(
    existingPersona: PersonaEntity? = null,
    soulRepository: SoulRepository,
    onDismiss: () -> Unit,
    onSave: (PersonaEntity) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(existingPersona?.name ?: "") }
    var age by remember { mutableStateOf(existingPersona?.age?.toString() ?: "30") }

    val genderOptions = listOf("Female", "Male", "Non-binary", "Other", "Custom...")
    var gender by remember { mutableStateOf(existingPersona?.gender ?: "Female") }
    var isCustomGender by remember { mutableStateOf(existingPersona?.gender != null && existingPersona.gender !in genderOptions.dropLast(1)) }
    var customGenderText by remember { mutableStateOf(if (isCustomGender) gender else "") }

    val relationshipOptions = listOf(
        "Friend", "Girlfriend", "Boyfriend", "Mentor", "Parent",
        "Sibling", "Counselor", "Partner", "Colleague", "Custom..."
    )
    var relationship by remember { mutableStateOf(existingPersona?.relationship ?: "Friend") }
    var isCustomRelationship by remember { mutableStateOf(existingPersona?.relationship != null && existingPersona.relationship !in relationshipOptions.dropLast(1)) }
    var customRelationshipText by remember { mutableStateOf(if (isCustomRelationship) relationship else "") }

    val avatarStyleOptions = listOf(
        "Avataaars (Modern)", "DiceBear / Cartoon", "Pixel Art", "Realistic", "Custom Upload", "Custom..."
    )
    var avatarStyle by remember { mutableStateOf(existingPersona?.avatarStyle ?: "Avataaars (Modern)") }
    var isCustomAvatarStyle by remember { mutableStateOf(existingPersona?.avatarStyle != null && existingPersona.avatarStyle !in avatarStyleOptions.dropLast(1)) }
    var customAvatarStyleText by remember { mutableStateOf(if (isCustomAvatarStyle) avatarStyle else "") }

    var avatarSeed by remember { mutableStateOf(existingPersona?.avatarSeed ?: "") }
    var avatarUri by remember { mutableStateOf(existingPersona?.avatarUri) }
    var avatarBlob by remember { mutableStateOf<ByteArray?>(existingPersona?.avatarBlob) }

    var traitInput by remember { mutableStateOf("") }
    val traitsList = remember {
        mutableStateListOf<String>().apply {
            if (!existingPersona?.traits.isNullOrBlank()) {
                addAll(existingPersona!!.traits.split(",").map { it.trim() }.filter { it.isNotBlank() })
            }
        }
    }

    var eyeColor by remember { mutableStateOf(existingPersona?.eyeColor ?: "") }
    var hairStyle by remember { mutableStateOf(existingPersona?.hairStyle ?: "") }
    var height by remember { mutableStateOf(existingPersona?.height ?: "") }
    var build by remember { mutableStateOf(existingPersona?.build ?: "") }
    var clothingStyle by remember { mutableStateOf(existingPersona?.clothingStyle ?: "") }
    var keyFeatures by remember { mutableStateOf(existingPersona?.keyFeatures ?: "") }

    var bio by remember { mutableStateOf(existingPersona?.bio ?: "") }
    var customChatBgUri by remember { mutableStateOf(existingPersona?.customChatBgUri ?: "") }
    var customChatBgBlob by remember { mutableStateOf<ByteArray?>(existingPersona?.customChatBgBlob) }
    var customChatBgOpacity by remember { mutableFloatStateOf(existingPersona?.customChatBgOpacity ?: 0.8f) }

    var emotionInput by remember { mutableStateOf("") }
    var emotionPercentInput by remember { mutableStateOf("50") }
    val emotionsList = remember {
        mutableStateListOf<EmotionItem>().apply {
            if (existingPersona != null) {
                addAll(soulRepository.parseEmotions(existingPersona.emotionsJson))
            }
        }
    }

    // Dropdown expanded states
    var genderExpanded by remember { mutableStateOf(false) }
    var relationshipExpanded by remember { mutableStateOf(false) }
    var avatarStyleExpanded by remember { mutableStateOf(false) }

    // Image Pickers
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            avatarUri = it.toString()
            avatarBlob = ImageUtils.uriToCompressedBlob(context, it)
        }
    }

    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            customChatBgUri = it.toString()
            customChatBgBlob = ImageUtils.uriToCompressedBlob(context, it)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("create_persona_sheet"),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (existingPersona == null) "Create AI Persona" else "Edit AI Persona",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_persona_sheet")
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Name Field
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("e.g. Elon Musk, Mother, Father") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("persona_name_input"),
                    singleLine = true,
                    colors = customTextFieldColors()
                )

                // Age Field
                OutlinedTextField(
                    value = age,
                    onValueChange = { age = it.filter { char -> char.isDigit() } },
                    label = { Text("Age") },
                    placeholder = { Text("30") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("persona_age_input"),
                    singleLine = true,
                    colors = customTextFieldColors()
                )

                // Gender Dropdown
                ExposedDropdownMenuBox(
                    expanded = genderExpanded,
                    onExpandedChange = { genderExpanded = !genderExpanded }
                ) {
                    OutlinedTextField(
                        value = if (isCustomGender) "Custom (${customGenderText.ifBlank { "Unspecified" }})" else gender,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Gender") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        colors = customTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = genderExpanded,
                        onDismissRequest = { genderExpanded = false }
                    ) {
                        genderOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    if (option == "Custom...") {
                                        isCustomGender = true
                                        gender = customGenderText.ifBlank { "Custom" }
                                    } else {
                                        isCustomGender = false
                                        gender = option
                                    }
                                    genderExpanded = false
                                }
                            )
                        }
                    }
                }

                if (isCustomGender) {
                    OutlinedTextField(
                        value = customGenderText,
                        onValueChange = {
                            customGenderText = it
                            gender = it
                        },
                        label = { Text("Custom Gender") },
                        placeholder = { Text("e.g. Android, Shapeshifter") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_gender_input"),
                        singleLine = true,
                        colors = customTextFieldColors()
                    )
                }

                // Relationship Dropdown
                ExposedDropdownMenuBox(
                    expanded = relationshipExpanded,
                    onExpandedChange = { relationshipExpanded = !relationshipExpanded }
                ) {
                    OutlinedTextField(
                        value = if (isCustomRelationship) "Custom (${customRelationshipText.ifBlank { "Unspecified" }})" else relationship,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Relationship to You") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = relationshipExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        colors = customTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = relationshipExpanded,
                        onDismissRequest = { relationshipExpanded = false }
                    ) {
                        relationshipOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    if (option == "Custom...") {
                                        isCustomRelationship = true
                                        relationship = customRelationshipText.ifBlank { "Custom" }
                                    } else {
                                        isCustomRelationship = false
                                        relationship = option
                                    }
                                    relationshipExpanded = false
                                }
                            )
                        }
                    }
                }
                
                if (isCustomRelationship) {
                    OutlinedTextField(
                        value = customRelationshipText,
                        onValueChange = {
                            customRelationshipText = it
                            relationship = it
                        },
                        label = { Text("Custom Relationship") },
                        placeholder = { Text("e.g. Life Coach, Step-sister, Fictional Hero") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_relationship_input"),
                        singleLine = true,
                        colors = customTextFieldColors()
                    )
                }

                // Avatar Section
                Text(
                    text = "Avatar (Select style or Upload Image)",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        ExposedDropdownMenuBox(
                            expanded = avatarStyleExpanded,
                            onExpandedChange = { avatarStyleExpanded = !avatarStyleExpanded }
                        ) {
                            OutlinedTextField(
                                value = if (isCustomAvatarStyle) "Custom (${customAvatarStyleText.ifBlank { "Style" }})" else avatarStyle,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = avatarStyleExpanded) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth(),
                                colors = customTextFieldColors()
                            )
                            ExposedDropdownMenu(
                                expanded = avatarStyleExpanded,
                                onDismissRequest = { avatarStyleExpanded = false }
                            ) {
                                avatarStyleOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            if (option == "Custom...") {
                                                isCustomAvatarStyle = true
                                                avatarStyle = customAvatarStyleText.ifBlank { "Custom" }
                                            } else {
                                                isCustomAvatarStyle = false
                                                avatarStyle = option
                                            }
                                            avatarStyleExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = avatarSeed,
                        onValueChange = { avatarSeed = it },
                        placeholder = { Text("Avatar seed") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = customTextFieldColors()
                    )
                }

                if (isCustomAvatarStyle) {
                    OutlinedTextField(
                        value = customAvatarStyleText,
                        onValueChange = {
                            customAvatarStyleText = it
                            avatarStyle = it
                        },
                        label = { Text("Custom Avatar Style") },
                        placeholder = { Text("e.g. Anime, Cyberpunk, Oil Painting") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_avatar_style_input"),
                        singleLine = true,
                        colors = customTextFieldColors()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { avatarPicker.launch("image/*") },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (avatarUri != null) "Change File" else "Choose File")
                    }

                    Text(
                        text = if (avatarUri != null) "File selected" else "No file chosen",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }

                // Avatar Preview Circle
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AvatarView(
                        name = name.ifBlank { "Persona" },
                        avatarStyle = avatarStyle,
                        avatarSeed = avatarSeed,
                        avatarUri = avatarUri,
                        avatarBlob = avatarBlob,
                        size = 80.dp
                    )
                }

                // Traits Input
                Text(
                    text = "Traits",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = traitInput,
                        onValueChange = { traitInput = it },
                        placeholder = { Text("e.g. Caring, Sarcastic, Wealthy") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = customTextFieldColors()
                    )
                    Button(
                        onClick = {
                            if (traitInput.isNotBlank()) {
                                traitsList.add(traitInput.trim())
                                traitInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Add", color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                // Traits Chips
                if (traitsList.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        traitsList.forEach { trait ->
                            InputChip(
                                selected = true,
                                onClick = { traitsList.remove(trait) },
                                label = { Text(trait, color = MaterialTheme.colorScheme.onSurface) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = InputChipDefaults.inputChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            )
                        }
                    }
                }

                // Appearance Details Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Appearance Details",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = eyeColor,
                                onValueChange = { eyeColor = it },
                                label = { Text("Eye Color") },
                                placeholder = { Text("e.g. Brown") },
                                modifier = Modifier.weight(1f),
                                colors = customTextFieldColors()
                            )
                            OutlinedTextField(
                                value = hairStyle,
                                onValueChange = { hairStyle = it },
                                label = { Text("Hair Style") },
                                placeholder = { Text("e.g. Short dark hair") },
                                modifier = Modifier.weight(1f),
                                colors = customTextFieldColors()
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = height,
                                onValueChange = { height = it },
                                label = { Text("Height") },
                                placeholder = { Text("e.g. 5'5") },
                                modifier = Modifier.weight(1f),
                                colors = customTextFieldColors()
                            )
                            OutlinedTextField(
                                value = build,
                                onValueChange = { build = it },
                                label = { Text("Build") },
                                placeholder = { Text("e.g. Average") },
                                modifier = Modifier.weight(1f),
                                colors = customTextFieldColors()
                            )
                        }

                        OutlinedTextField(
                            value = clothingStyle,
                            onValueChange = { clothingStyle = it },
                            label = { Text("Clothing Style") },
                            placeholder = { Text("e.g. Casual, aprons") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = customTextFieldColors()
                        )

                        OutlinedTextField(
                            value = keyFeatures,
                            onValueChange = { keyFeatures = it },
                            label = { Text("Key Features") },
                            placeholder = { Text("e.g. Glasses, tattoos") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = customTextFieldColors()
                        )
                    }
                }

                // Persona Background / Biography
                Text(
                    text = "Persona Background / Biography",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    placeholder = { Text("Describe their life, context, behavior style...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                    colors = customTextFieldColors()
                )

                // Custom Chat Background Image & Opacity
                Text(
                    text = "Custom Chat Background Image",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customChatBgUri ?: "",
                        onValueChange = { customChatBgUri = it },
                        placeholder = { Text("Paste image URL (https://...)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = customTextFieldColors()
                    )
                    Button(
                        onClick = { bgPicker.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text("Upload File", color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                if (customChatBgBlob != null || !customChatBgUri.isNullOrBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Background Opacity",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${(customChatBgOpacity * 100).toInt()}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = customChatBgOpacity,
                            onValueChange = { customChatBgOpacity = it },
                            valueRange = 0.05f..1.0f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Emotional Attachment to User
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Emotional Attachment to User",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )

                        Text(
                            text = "AI automatically evaluates and updates these emotional attachment percentages based on ongoing chats. You can also set initial baseline values below:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = emotionInput,
                                onValueChange = { emotionInput = it },
                                placeholder = { Text("Emotion (e.g. Love, Care)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = customTextFieldColors()
                            )

                            OutlinedTextField(
                                value = emotionPercentInput,
                                onValueChange = { emotionPercentInput = it.filter { c -> c.isDigit() } },
                                placeholder = { Text("50") },
                                trailingIcon = { Text("%", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = customTextFieldColors()
                            )

                            Button(
                                onClick = {
                                    if (emotionInput.isNotBlank()) {
                                        val percent = emotionPercentInput.toIntOrNull()?.coerceIn(0, 100) ?: 50
                                        emotionsList.add(EmotionItem(emotionInput.trim(), percent))
                                        emotionInput = ""
                                        emotionPercentInput = "50"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Add", color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }

                        if (emotionsList.isEmpty()) {
                            Text(
                                text = "No emotions set — persona will behave neutrally.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                emotionsList.forEach { item ->
                                    InputChip(
                                        selected = true,
                                        onClick = { emotionsList.remove(item) },
                                        label = { Text("${item.emotion} ${item.percentage}%", color = MaterialTheme.colorScheme.onSurface) },
                                        trailingIcon = {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove",
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        colors = InputChipDefaults.inputChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        if (name.isBlank()) return@Button
                        val parsedAge = age.toIntOrNull() ?: 25
                        val finalGender = if (isCustomGender) customGenderText.ifBlank { "Custom" } else gender
                        val finalRelationship = if (isCustomRelationship) customRelationshipText.ifBlank { "Custom" } else relationship
                        val finalAvatarStyle = if (isCustomAvatarStyle) customAvatarStyleText.ifBlank { "Custom" } else avatarStyle

                        val persona = (existingPersona ?: PersonaEntity(name = name)).copy(
                            name = name,
                            age = parsedAge,
                            gender = finalGender,
                            relationship = finalRelationship,
                            avatarStyle = finalAvatarStyle,
                            avatarSeed = avatarSeed,
                            avatarUri = avatarUri,
                            avatarBlob = avatarBlob,
                            traits = traitsList.joinToString(", "),
                            eyeColor = eyeColor,
                            hairStyle = hairStyle,
                            height = height,
                            build = build,
                            clothingStyle = clothingStyle,
                            keyFeatures = keyFeatures,
                            bio = bio,
                            customChatBgUri = customChatBgUri.ifBlank { null },
                            customChatBgBlob = customChatBgBlob,
                            customChatBgOpacity = customChatBgOpacity,
                            emotionsJson = soulRepository.serializeEmotions(emotionsList.toList())
                        )
                        onSave(persona)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_persona_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(if (existingPersona == null) "Create Persona" else "Save Changes", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}
