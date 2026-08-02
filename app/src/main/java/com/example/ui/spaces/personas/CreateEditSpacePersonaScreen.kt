package com.example.ui.spaces.personas

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.spaces.model.AppearanceModel
import com.example.data.spaces.model.PlaceModel
import com.example.data.spaces.model.SpaceModel
import com.example.data.spaces.model.SpacePersonaModel
import com.example.data.spaces.model.UserCharacterModel
import com.example.ui.components.ImageViewerDialog
import com.example.ui.theme.customTextFieldColors
import com.example.util.AgeUtil
import com.example.util.compressImageToJpegBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditSpacePersonaScreen(
    space: SpaceModel,
    existingPersona: SpacePersonaModel?,
    existingUserCharacter: UserCharacterModel?,
    otherPersonas: List<SpacePersonaModel>,
    isUserCharacterMode: Boolean,
    places: List<PlaceModel> = emptyList(),
    prefillName: String = "",
    onBack: () -> Unit,
    onSavePersona: (SpacePersonaModel) -> Unit,
    onSaveUserCharacter: (UserCharacterModel) -> Unit,
    onUploadImage: suspend (personaId: String, kind: String, bytes: ByteArray, mimeType: String) -> Result<String>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val personaId = remember { existingPersona?.id?.ifBlank { null } ?: UUID.randomUUID().toString() }

    // Keyed by the incoming entities: the Firestore listener behind existingUserCharacter (and,
    // in principle, existingPersona) can emit null on the very first composition before the real
    // document arrives a frame later. remember{} without a key only captures its INITIAL value,
    // so without this key the form would silently stay blank forever even once real data shows
    // up -- this was exactly why "Your Character" never autofilled.
    var name by remember(existingPersona, existingUserCharacter) { mutableStateOf(existingPersona?.name ?: existingUserCharacter?.name ?: prefillName) }
    var dob by remember(existingPersona, existingUserCharacter) { mutableStateOf(existingPersona?.dob ?: existingUserCharacter?.dob ?: "") }
    var background by remember(existingPersona, existingUserCharacter) { mutableStateOf(existingPersona?.background ?: existingUserCharacter?.background ?: "") }

    var relationshipToUser by remember(existingPersona) { mutableStateOf(existingPersona?.relationshipToUser ?: "") }
    var bio by remember(existingPersona) { mutableStateOf(existingPersona?.bio ?: "") }
    var mood by remember(existingPersona) { mutableFloatStateOf((existingPersona?.mood ?: 0).toFloat()) }
    var aggressiveness by remember(existingPersona) { mutableFloatStateOf((existingPersona?.aggressiveness ?: 0).toFloat()) }
    val relationshipTones = remember(existingPersona) {
        mutableStateMapOf<String, String>().apply {
            existingPersona?.relationshipsToOtherPersonas?.let { putAll(it) }
        }
    }

    val initialAppearance = existingPersona?.appearance ?: existingUserCharacter?.appearance ?: AppearanceModel()
    var hairColor by remember(existingPersona, existingUserCharacter) { mutableStateOf(initialAppearance.hairColor) }
    var hairStyle by remember(existingPersona, existingUserCharacter) { mutableStateOf(initialAppearance.hairStyle) }
    var eyeColor by remember(existingPersona, existingUserCharacter) { mutableStateOf(initialAppearance.eyeColor) }
    var skinTone by remember(existingPersona, existingUserCharacter) { mutableStateOf(initialAppearance.skinTone) }
    var build by remember(existingPersona, existingUserCharacter) { mutableStateOf(initialAppearance.build) }
    var height by remember(existingPersona, existingUserCharacter) { mutableStateOf(initialAppearance.height) }
    var extraFeatures by remember(existingPersona, existingUserCharacter) { mutableStateOf(initialAppearance.extraFeatures) }

    var currentPlaceId by remember(existingUserCharacter) { mutableStateOf(existingUserCharacter?.currentPlaceId ?: "") }
    var currentPlaceName by remember(existingUserCharacter) { mutableStateOf(existingUserCharacter?.currentPlaceName ?: "") }
    var placePickerExpanded by remember { mutableStateOf(false) }

    var avatarImageUrl by remember(existingPersona) { mutableStateOf(existingPersona?.avatarImageUrl ?: "") }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var avatarError by remember { mutableStateOf<String?>(null) }

    var chatBackgroundImageUrl by remember(existingPersona) { mutableStateOf(existingPersona?.chatBackgroundImageUrl ?: "") }
    var chatBackgroundOpacity by remember(existingPersona) { mutableFloatStateOf(existingPersona?.chatBackgroundOpacity ?: 1f) }
    var isUploadingBackground by remember { mutableStateOf(false) }
    var backgroundError by remember { mutableStateOf<String?>(null) }

    val portfolioImageUrls = remember(existingPersona) {
        mutableStateListOf<String>().apply { addAll(existingPersona?.portfolioImageUrls ?: emptyList()) }
    }
    var isUploadingPortfolio by remember { mutableStateOf(false) }
    var portfolioError by remember { mutableStateOf<String?>(null) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    fun addToPortfolio(url: String) {
        if (url.isNotBlank() && !portfolioImageUrls.contains(url)) portfolioImageUrls.add(url)
    }

    val avatarPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isUploadingAvatar = true
            avatarError = null
            val bytes = withContext(Dispatchers.Default) {
                compressImageToJpegBytes(context, uri, maxDimension = 512, maxBytes = 300_000)
            }
            if (bytes == null) {
                avatarError = "Couldn't read that photo."
            } else {
                onUploadImage(personaId, "avatar", bytes, "image/jpeg")
                    .onSuccess { url -> avatarImageUrl = url; addToPortfolio(url) }
                    .onFailure { avatarError = it.localizedMessage ?: "Upload failed." }
            }
            isUploadingAvatar = false
        }
    }

    val backgroundPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isUploadingBackground = true
            backgroundError = null
            val bytes = withContext(Dispatchers.Default) {
                compressImageToJpegBytes(context, uri, maxDimension = 1600, maxBytes = 900_000)
            }
            if (bytes == null) {
                backgroundError = "Couldn't read that photo."
            } else {
                onUploadImage(personaId, "background", bytes, "image/jpeg")
                    .onSuccess { url -> chatBackgroundImageUrl = url; addToPortfolio(url) }
                    .onFailure { backgroundError = it.localizedMessage ?: "Upload failed." }
            }
            isUploadingBackground = false
        }
    }

    val portfolioPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isUploadingPortfolio = true
            portfolioError = null
            val bytes = withContext(Dispatchers.Default) {
                compressImageToJpegBytes(context, uri, maxDimension = 1600, maxBytes = 900_000)
            }
            if (bytes == null) {
                portfolioError = "Couldn't read that photo."
            } else {
                onUploadImage(personaId, "portfolio", bytes, "image/jpeg")
                    .onSuccess { url -> addToPortfolio(url) }
                    .onFailure { portfolioError = it.localizedMessage ?: "Upload failed." }
            }
            isUploadingPortfolio = false
        }
    }

    fun handleSave() {
        if (isUserCharacterMode) {
            onSaveUserCharacter(
                UserCharacterModel(
                    name = name.trim(),
                    dob = dob.trim(),
                    background = background.trim(),
                    appearance = AppearanceModel(hairColor, hairStyle, eyeColor, skinTone, build, height, extraFeatures),
                    currentPlaceId = currentPlaceId,
                    currentPlaceName = currentPlaceName
                )
            )
        } else {
            onSavePersona(
                SpacePersonaModel(
                    id = personaId,
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
                    avatarSeed = existingPersona?.avatarSeed ?: name,
                    avatarImageUrl = avatarImageUrl,
                    chatBackgroundImageUrl = chatBackgroundImageUrl,
                    chatBackgroundOpacity = chatBackgroundOpacity,
                    portfolioImageUrls = portfolioImageUrls.toList()
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

            if (isUserCharacterMode && places.isNotEmpty()) {
                Text("Your location", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                ExposedDropdownMenuBox(expanded = placePickerExpanded, onExpandedChange = { placePickerExpanded = it }) {
                    OutlinedTextField(
                        value = currentPlaceName.ifBlank { "Not set" },
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = placePickerExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = RoundedCornerShape(12.dp),
                        colors = customTextFieldColors()
                    )
                    ExposedDropdownMenu(expanded = placePickerExpanded, onDismissRequest = { placePickerExpanded = false }) {
                        places.forEach { place ->
                            DropdownMenuItem(
                                text = { Text(place.name) },
                                onClick = {
                                    currentPlaceId = place.id
                                    currentPlaceName = place.name
                                    placePickerExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            if (!isUserCharacterMode) {
                Text(
                    text = "Photos",
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
                        if (avatarImageUrl.isNotBlank()) {
                            AsyncImage(
                                model = avatarImageUrl,
                                contentDescription = "Profile photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Column {
                        OutlinedButton(
                            onClick = { avatarPickerLauncher.launch("image/*") },
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            enabled = !isUploadingAvatar
                        ) {
                            Text(if (isUploadingAvatar) "Uploading…" else "Choose profile photo", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (avatarError != null) {
                            Text(avatarError ?: "", fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }

                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (chatBackgroundImageUrl.isNotBlank()) {
                            AsyncImage(
                                model = chatBackgroundImageUrl,
                                contentDescription = "Chat background",
                                contentScale = ContentScale.Crop,
                                alpha = chatBackgroundOpacity,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { backgroundPickerLauncher.launch("image/*") },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        enabled = !isUploadingBackground
                    ) {
                        Text(if (isUploadingBackground) "Uploading…" else "Choose chat background", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (backgroundError != null) {
                        Text(backgroundError ?: "", fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (chatBackgroundImageUrl.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text("Background opacity", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Text("${(chatBackgroundOpacity * 100).toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        Slider(value = chatBackgroundOpacity, onValueChange = { chatBackgroundOpacity = it }, valueRange = 0f..1f, modifier = Modifier.fillMaxWidth())
                    }
                }

                Column {
                    Text(
                        text = "Portfolio",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Every photo you upload lands here too -- tap one to use it as the profile photo or chat background.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(portfolioImageUrls) { index, url ->
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { viewerIndex = index }
                            ) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = "Portfolio photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (url == avatarImageUrl || url == chatBackgroundImageUrl) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                                            .padding(vertical = 3.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (url == avatarImageUrl) "Profile" else "Background",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable(enabled = !isUploadingPortfolio) { portfolioPickerLauncher.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isUploadingPortfolio) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.AddAPhoto, contentDescription = "Add to portfolio", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    if (portfolioError != null) {
                        Text(portfolioError ?: "", fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            if (viewerIndex != null) {
                ImageViewerDialog(
                    images = portfolioImageUrls,
                    initialIndex = viewerIndex!!,
                    onDismiss = { viewerIndex = null },
                    actions = { url ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { avatarImageUrl = url },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                                modifier = Modifier.weight(1f)
                            ) { Text("Profile photo", fontSize = 11.sp) }
                            OutlinedButton(
                                onClick = { chatBackgroundImageUrl = url },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                                modifier = Modifier.weight(1f)
                            ) { Text("Background", fontSize = 11.sp) }
                            OutlinedButton(
                                onClick = {
                                    val removedIndex = portfolioImageUrls.indexOf(url)
                                    portfolioImageUrls.remove(url)
                                    if (avatarImageUrl == url) avatarImageUrl = ""
                                    if (chatBackgroundImageUrl == url) chatBackgroundImageUrl = ""
                                    viewerIndex = when {
                                        portfolioImageUrls.isEmpty() -> null
                                        removedIndex >= portfolioImageUrls.size -> portfolioImageUrls.size - 1
                                        else -> removedIndex
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                                modifier = Modifier.weight(1f)
                            ) { Text("Delete", fontSize = 11.sp) }
                        }
                    }
                )
            }

            // Appearance
            Text(
                text = "Appearance",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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
