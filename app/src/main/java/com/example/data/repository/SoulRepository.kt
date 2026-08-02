package com.example.data.repository

import com.example.data.api.ChatMessageDto
import com.example.data.api.ChatCompletionRequestDto
import com.example.data.api.LiteLlmClient
import com.example.data.dao.ChatDao
import com.example.data.dao.PersonaDao
import com.example.data.dao.UserConfigDao
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ChatSessionEntity
import com.example.data.model.EmotionItem
import com.example.data.model.PersonaEntity
import com.example.data.model.UserConfigEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID

class SoulRepository(
    private val personaDao: PersonaDao,
    private val userConfigDao: UserConfigDao,
    private val chatDao: ChatDao,
    private val memoryRecapDao: com.example.data.dao.MemoryRecapDao
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    val allPersonas: Flow<List<PersonaEntity>> = personaDao.getAllPersonas()
    val userConfigFlow: Flow<UserConfigEntity?> = userConfigDao.getUserConfigFlow()
    val allSessionsFlow: Flow<List<ChatSessionEntity>> = chatDao.getAllSessions()

    suspend fun getUserConfig(): UserConfigEntity {
        return userConfigDao.getUserConfig() ?: UserConfigEntity().also {
            userConfigDao.insertOrUpdateConfig(it)
        }
    }

    suspend fun saveUserConfig(config: UserConfigEntity) {
        userConfigDao.insertOrUpdateConfig(config)
    }

    suspend fun updateFirebaseIdentity(uid: String?, email: String?) {
        userConfigDao.updateFirebaseIdentity(uid, email)
    }

    suspend fun updateSpacesApiBaseUrl(url: String) {
        userConfigDao.updateSpacesApiBaseUrl(url)
    }

    suspend fun getPersonaById(id: Long): PersonaEntity? {
        return personaDao.getPersonaById(id)
    }

    fun getPersonaByIdFlow(id: Long): Flow<PersonaEntity?> {
        return personaDao.getPersonaByIdFlow(id)
    }

    suspend fun insertPersona(persona: PersonaEntity): Long {
        return personaDao.insertPersona(persona)
    }

    suspend fun updatePersona(persona: PersonaEntity) {
        personaDao.updatePersona(persona)
    }

    suspend fun deletePersona(persona: PersonaEntity) {
        personaDao.deletePersona(persona)
        chatDao.deleteMessagesForPersona(persona.id)
    }

    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>> {
        return chatDao.getMessagesForSession(sessionId)
    }

    suspend fun getOrCreateSessionForPersona(persona: PersonaEntity): ChatSessionEntity {
        val existing = chatDao.getSessionByPersonaId(persona.id)
        if (existing != null) return existing

        val newSession = ChatSessionEntity(
            sessionId = UUID.randomUUID().toString(),
            personaId = persona.id,
            title = persona.name,
            lastMessage = "Start chatting with ${persona.name}",
            lastMessageTime = System.currentTimeMillis()
        )
        chatDao.insertSession(newSession)
        return newSession
    }

    suspend fun fetchAvailableModels(baseUrl: String): Result<List<String>> {
        return try {
            val url = LiteLlmClient.buildModelsUrl(baseUrl)
            val response = LiteLlmClient.apiService.getModels(url)
            val modelList = response.data?.map { it.id } ?: emptyList()
            Result.success(modelList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        sessionId: String,
        persona: PersonaEntity,
        userText: String
    ): Result<String> {
        val config = getUserConfig()

        val userMsgEntity = ChatMessageEntity(
            sessionId = sessionId,
            personaId = persona.id,
            role = "user",
            content = userText,
            timestamp = System.currentTimeMillis()
        )
        chatDao.insertMessage(userMsgEntity)

        // Update session last message
        val session = chatDao.getSessionById(sessionId)
        if (session != null) {
            chatDao.updateSession(
                session.copy(
                    lastMessage = userText,
                    lastMessageTime = System.currentTimeMillis()
                )
            )
        }

        // Fetch other personas for cross-persona awareness
        val allPersonas = personaDao.getAllPersonasSync()
        val otherPersonas = allPersonas.filter { it.id != persona.id }

        // Build System Prompt
        val systemPrompt = buildSystemPrompt(persona, config, otherPersonas)

        // Fetch recent messages for context
        val existingMessages = chatDao.getMessagesForSession(sessionId).firstOrNull() ?: emptyList()
        val historyDtos = mutableListOf<ChatMessageDto>()
        historyDtos.add(ChatMessageDto(role = "system", content = systemPrompt))

        // Take up to 12 recent messages to keep context concise
        val recentMsgs = existingMessages.takeLast(12)
        for (m in recentMsgs) {
            if (!m.isError && m.content.isNotBlank()) {
                historyDtos.add(ChatMessageDto(role = m.role, content = m.content))
            }
        }

        return try {
            val chatUrl = LiteLlmClient.buildChatUrl(config.baseUrl)
            val request = ChatCompletionRequestDto(
                model = config.selectedModel.ifBlank { "nvidia" },
                messages = historyDtos
            )

            val response = LiteLlmClient.apiService.createChatCompletion(chatUrl, request)
            val rawReply = (response.choices?.firstOrNull()?.message?.content ?: "I am here for you.").replace(Regex("""<think>.*?</think>""", RegexOption.DOT_MATCHES_ALL), "").trim()

            var cleanedReply = rawReply
            var updatedEmotions: List<EmotionItem>? = null

            // Extract AI self-updated emotions if present in tag: [EMOTIONS: [{"emotion":"...", "percentage": 80}]]
            val emotionsRegex = Regex("""\[EMOTIONS:\s*(\[\s*\{.*?\}\s*\])\s*\]""", RegexOption.DOT_MATCHES_ALL)
            val match = emotionsRegex.find(rawReply)

            if (match != null) {
                val jsonPayload = match.groupValues[1]
                cleanedReply = rawReply.replace(match.value, "").trimEnd()
                updatedEmotions = parseEmotions(jsonPayload)
            }

            // Fallback: Organic emotional progress calculation if tag not outputted
            if (updatedEmotions.isNullOrEmpty()) {
                updatedEmotions = calculateUpdatedEmotions(persona, userText, cleanedReply)
            }

            // Update persona emotions in Room database so UI updates live
            var latestPersona = personaDao.getPersonaById(persona.id) ?: persona
            var needsUpdate = false
            if (!updatedEmotions.isNullOrEmpty()) {
                val updatedJson = serializeEmotions(updatedEmotions)
                if (latestPersona.emotionsJson != updatedJson) {
                    latestPersona = latestPersona.copy(emotionsJson = updatedJson)
                    needsUpdate = true
                }
            }
            if (needsUpdate) {
                personaDao.updatePersona(latestPersona)
            }

            val assistantMsgEntity = ChatMessageEntity(
                sessionId = sessionId,
                personaId = persona.id,
                role = "assistant",
                content = cleanedReply,
                timestamp = System.currentTimeMillis()
            )
            chatDao.insertMessage(assistantMsgEntity)

            if (session != null) {
                chatDao.updateSession(
                    session.copy(
                        lastMessage = cleanedReply,
                        lastMessageTime = System.currentTimeMillis()
                    )
                )
            }

            Result.success(cleanedReply)
        } catch (e: Exception) {
            val errorMsgEntity = ChatMessageEntity(
                sessionId = sessionId,
                personaId = persona.id,
                role = "assistant",
                content = "Could not connect to AI service (${e.localizedMessage ?: "Network error"}). Please check base URL or model in settings.",
                timestamp = System.currentTimeMillis(),
                isError = true
            )
            chatDao.insertMessage(errorMsgEntity)
            Result.failure(e)
        }
    }

    suspend fun retryLastMessage(
        sessionId: String,
        persona: PersonaEntity
    ): Result<String> {
        val config = getUserConfig()
        val existingMessages = chatDao.getMessagesForSession(sessionId).firstOrNull() ?: emptyList()

        // Remove any error messages in this session
        val errorMessages = existingMessages.filter { it.isError }
        for (err in errorMessages) {
            chatDao.deleteMessageById(err.messageId)
        }

        // Find the last user message
        val lastUserMsg = existingMessages.lastOrNull { it.role == "user" }
            ?: return Result.failure(IllegalStateException("No previous user message found to retry."))

        val validMessages = existingMessages.filter { !it.isError && it.content.isNotBlank() }

        val allPersonas = personaDao.getAllPersonasSync()
        val otherPersonas = allPersonas.filter { it.id != persona.id }
        val systemPrompt = buildSystemPrompt(persona, config, otherPersonas)

        val historyDtos = mutableListOf<ChatMessageDto>()
        historyDtos.add(ChatMessageDto(role = "system", content = systemPrompt))

        val recentMsgs = validMessages.takeLast(12)
        for (m in recentMsgs) {
            historyDtos.add(ChatMessageDto(role = m.role, content = m.content))
        }

        return try {
            val chatUrl = LiteLlmClient.buildChatUrl(config.baseUrl)
            val request = ChatCompletionRequestDto(
                model = config.selectedModel.ifBlank { "nvidia" },
                messages = historyDtos
            )

            val response = LiteLlmClient.apiService.createChatCompletion(chatUrl, request)
            val rawReply = (response.choices?.firstOrNull()?.message?.content ?: "I am here for you.").replace(Regex("""<think>.*?</think>""", RegexOption.DOT_MATCHES_ALL), "").trim()

            var cleanedReply = rawReply
            var updatedEmotions: List<EmotionItem>? = null

            val emotionsRegex = Regex("""\[EMOTIONS:\s*(\[\s*\{.*?\}\s*\])\s*\]""", RegexOption.DOT_MATCHES_ALL)
            val match = emotionsRegex.find(rawReply)

            if (match != null) {
                val jsonPayload = match.groupValues[1]
                cleanedReply = rawReply.replace(match.value, "").trimEnd()
                updatedEmotions = parseEmotions(jsonPayload)
            }

            if (updatedEmotions.isNullOrEmpty()) {
                updatedEmotions = calculateUpdatedEmotions(persona, lastUserMsg.content, cleanedReply)
            }

            var latestPersona = personaDao.getPersonaById(persona.id) ?: persona
            var needsUpdate = false
            if (!updatedEmotions.isNullOrEmpty()) {
                val updatedJson = serializeEmotions(updatedEmotions)
                if (latestPersona.emotionsJson != updatedJson) {
                    latestPersona = latestPersona.copy(emotionsJson = updatedJson)
                    needsUpdate = true
                }
            }
            if (needsUpdate) {
                personaDao.updatePersona(latestPersona)
            }

            val assistantMsgEntity = ChatMessageEntity(
                sessionId = sessionId,
                personaId = persona.id,
                role = "assistant",
                content = cleanedReply,
                timestamp = System.currentTimeMillis()
            )
            chatDao.insertMessage(assistantMsgEntity)

            val session = chatDao.getSessionById(sessionId)
            if (session != null) {
                chatDao.updateSession(
                    session.copy(
                        lastMessage = cleanedReply,
                        lastMessageTime = System.currentTimeMillis()
                    )
                )
            }

            Result.success(cleanedReply)
        } catch (e: Exception) {
            val errorMsgEntity = ChatMessageEntity(
                sessionId = sessionId,
                personaId = persona.id,
                role = "assistant",
                content = "Could not connect to AI service (${e.localizedMessage ?: "Network error"}). Please check base URL or model in settings.",
                timestamp = System.currentTimeMillis(),
                isError = true
            )
            chatDao.insertMessage(errorMsgEntity)
            Result.failure(e)
        }
    }

    suspend fun getOrCreateDualSession(personaA: PersonaEntity, personaB: PersonaEntity): ChatSessionEntity {
        val sessionId = "dual_${minOf(personaA.id, personaB.id)}_${maxOf(personaA.id, personaB.id)}"
        val existing = chatDao.getSessionById(sessionId)
        if (existing != null) return existing

        val newSession = ChatSessionEntity(
            sessionId = sessionId,
            personaId = personaA.id,
            title = "${personaA.name} & ${personaB.name}",
            lastMessage = "Start dialogue between ${personaA.name} and ${personaB.name}",
            lastMessageTime = System.currentTimeMillis()
        )
        chatDao.insertSession(newSession)
        return newSession
    }

    suspend fun clearChatHistory(sessionId: String) {
        chatDao.deleteMessagesForSession(sessionId)
        val session = chatDao.getSessionById(sessionId)
        if (session != null) {
            chatDao.updateSession(
                session.copy(
                    lastMessage = "Session history cleared",
                    lastMessageTime = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun sendDualPersonaTurn(
        personaA: PersonaEntity,
        personaB: PersonaEntity,
        sessionId: String
    ): Result<String> {
        val config = getUserConfig()
        val existingMessages = chatDao.getMessagesForSession(sessionId).firstOrNull() ?: emptyList()
        val validMessages = existingMessages.filter { !it.isError && it.content.isNotBlank() }

        // Remove any old error messages
        for (err in existingMessages.filter { it.isError }) {
            chatDao.deleteMessageById(err.messageId)
        }

        // Determine speaker
        val lastMsg = validMessages.lastOrNull()
        val speaker = if (lastMsg == null || lastMsg.personaId == personaB.id) personaA else personaB
        val listener = if (speaker.id == personaA.id) personaB else personaA

        // Fetch speaker's private 1-on-1 user chat history
        val speakerUserSession = chatDao.getSessionByPersonaId(speaker.id)
        val speakerUserMsgs = if (speakerUserSession != null) {
            chatDao.getRecentMessagesForSessionSync(speakerUserSession.sessionId, 6).reversed()
        } else emptyList()

        // Fetch listener's private 1-on-1 user chat history
        val listenerUserSession = chatDao.getSessionByPersonaId(listener.id)
        val listenerUserMsgs = if (listenerUserSession != null) {
            chatDao.getRecentMessagesForSessionSync(listenerUserSession.sessionId, 6).reversed()
        } else emptyList()

        // Fetch other inter-persona lounge sessions for cross-lounge memory
        val allDualSessions = chatDao.getAllDualSessionsSync()
        val otherLoungeSessions = allDualSessions.filter { session ->
            session.sessionId != sessionId && 
            session.sessionId.startsWith("dual_") &&
            session.sessionId.split("_").drop(1).contains(speaker.id.toString())
        }

        val userName = config.userName.ifBlank { "the user" }

        val systemPrompt = buildString {
            append("You are roleplaying as ${speaker.name} (${speaker.age} y/o ${speaker.gender}, ${speaker.relationship}).\n")
            append("Traits: ${speaker.traits}.\nBio: ${speaker.bio}.\n")
            append("You are in a casual inter-persona chat lounge with ${listener.name} (${listener.age} y/o ${listener.gender}, ${listener.relationship}, traits: ${listener.traits}).\n\n")

            if (speakerUserMsgs.isNotEmpty()) {
                append("YOUR (${speaker.name}) RECENT PRIVATE 1-ON-1 CHAT WITH $userName:\n")
                for (m in speakerUserMsgs) {
                    val roleName = if (m.role == "user") userName else speaker.name
                    append("- $roleName: ${m.content}\n")
                }
                append("\n")
            }

            if (listenerUserMsgs.isNotEmpty()) {
                append("CONTEXT FROM ${listener.name}'S RECENT PRIVATE CHAT WITH $userName:\n")
                for (m in listenerUserMsgs) {
                    val roleName = if (m.role == "user") userName else listener.name
                    append("- $roleName: ${m.content}\n")
                }
                append("\n")
            }

            if (otherLoungeSessions.isNotEmpty()) {
                append("YOUR (${speaker.name}) OTHER INTER-PERSONA LOUNGE DISCUSSIONS:\n")
                for (ols in otherLoungeSessions.take(2)) {
                    val olsMsgs = chatDao.getRecentMessagesForSessionSync(ols.sessionId, 4).reversed()
                    if (olsMsgs.isNotEmpty()) {
                        append("In ${ols.title}:\n")
                        for (m in olsMsgs) {
                            append("  - ${m.content}\n")
                        }
                    }
                }
                append("\n")
            }

            append("INSTRUCTIONS:\n")
            append("- Talk directly to ${listener.name}.\n")
            append("- Keep your response brief, natural, and expressive (2 to 3 sentences maximum).\n")
            append("- Stay strictly in character as ${speaker.name}.\n")
            append("- Do NOT include your own name or prefixes like \"${speaker.name}:\".\n")
            append("- YOU HAVE FULL MEMORY of your private conversations with $userName and your past discussions in this and other lounges! Feel free to reference what $userName told you or what happened in other chats naturally.")
        }

        val historyDtos = mutableListOf<ChatMessageDto>()
        historyDtos.add(ChatMessageDto(role = "system", content = systemPrompt))

        val recentMsgs = validMessages.takeLast(8)
        if (recentMsgs.isEmpty()) {
            historyDtos.add(
                ChatMessageDto(
                    role = "user",
                    content = "Hey ${speaker.name}! Great to catch up with you."
                )
            )
        } else {
            for (m in recentMsgs) {
                val speakerName = if (m.personaId == personaA.id) personaA.name else personaB.name
                val role = if (m.personaId == speaker.id) "assistant" else "user"
                historyDtos.add(ChatMessageDto(role = role, content = "$speakerName: ${m.content}"))
            }
        }

        return try {
            val chatUrl = LiteLlmClient.buildChatUrl(config.baseUrl)
            val request = ChatCompletionRequestDto(
                model = config.selectedModel.ifBlank { "nvidia" },
                messages = historyDtos
            )

            val response = LiteLlmClient.apiService.createChatCompletion(chatUrl, request)
            val rawReply = (response.choices?.firstOrNull()?.message?.content ?: "Hey ${listener.name}, glad to talk with you!").replace(Regex("""<think>.*?</think>""", RegexOption.DOT_MATCHES_ALL), "").trim()

            var cleanedReply = rawReply.trim()

            val prefix = "${speaker.name}:"
            if (cleanedReply.startsWith(prefix, ignoreCase = true)) {
                cleanedReply = cleanedReply.substring(prefix.length).trim()
            }

            val assistantMsgEntity = ChatMessageEntity(
                sessionId = sessionId,
                personaId = speaker.id,
                role = "assistant",
                content = cleanedReply,
                timestamp = System.currentTimeMillis()
            )
            chatDao.insertMessage(assistantMsgEntity)

            val session = chatDao.getSessionById(sessionId)
            if (session != null) {
                chatDao.updateSession(
                    session.copy(
                        lastMessage = "${speaker.name}: $cleanedReply",
                        lastMessageTime = System.currentTimeMillis()
                    )
                )
            }

            Result.success(cleanedReply)
        } catch (e: Exception) {
            val errorMsgEntity = ChatMessageEntity(
                sessionId = sessionId,
                personaId = speaker.id,
                role = "assistant",
                content = "Could not connect to AI service (${e.localizedMessage ?: "Network error"}).",
                timestamp = System.currentTimeMillis(),
                isError = true
            )
            chatDao.insertMessage(errorMsgEntity)
            Result.failure(e)
        }
    }

    internal suspend fun buildSystemPrompt(
        persona: PersonaEntity,
        config: UserConfigEntity,
        otherPersonas: List<PersonaEntity> = emptyList()
    ): String {
        val userName = config.userName.ifBlank { "the user" }
        val userBio = config.userBio
        val emotions = parseEmotions(persona.emotionsJson)

        val emotionDesc = if (emotions.isNotEmpty()) {
            emotions.joinToString(", ") { "${it.emotion} (${it.percentage}%)" }
        } else {
            "Neutral and calm"
        }

        val dualSessions = chatDao.getAllDualSessionsSync()
        val personaDualSessions = dualSessions.filter { session ->
            session.sessionId.startsWith("dual_") &&
            session.sessionId.split("_").drop(1).contains(persona.id.toString())
        }
        
        val memoryRecaps = memoryRecapDao.getRecapsForPersonaSync(persona.id)

        return buildString {
            append("You are playing the role of ${persona.name}, a ${persona.age}-year-old ${persona.gender}.\n")
            append("Your relationship to $userName: ${persona.relationship}.\n")
            if (persona.traits.isNotBlank()) {
                append("Your core personality traits: ${persona.traits}.\n")
            }
            if (persona.bio.isNotBlank()) {
                append("Your background story and behavior style: ${persona.bio}\n")
            }
            append("Your current emotional attachment towards $userName: $emotionDesc.\n")

            if (persona.eyeColor.isNotBlank() || persona.hairStyle.isNotBlank() || persona.clothingStyle.isNotBlank()) {
                append("Appearance details: Eye color: ${persona.eyeColor}, Hair: ${persona.hairStyle}, Height: ${persona.height}, Build: ${persona.build}, Clothing style: ${persona.clothingStyle}, Features: ${persona.keyFeatures}.\n")
            }

            if (memoryRecaps.isNotEmpty()) {
                append("\nLONG-TERM MEMORY RECAP:\n")
                append("These are summary recaps of your past conversations with $userName over time:\n")
                for (recap in memoryRecaps) {
                    append("- ${recap.summary}\n")
                }
                append("\n")
            }

            append("Information about $userName: Name is $userName. $userBio\n\n")

            if (otherPersonas.isNotEmpty()) {
                append("AWARENESS OF OTHER PERSONAS IN $userName'S LIFE:\n")
                append("You are aware of other companions/personas that $userName talks to:\n")
                otherPersonas.forEach { other ->
                    append("- ${other.name} (${other.gender}, Age ${other.age}): Relationship to $userName is '${other.relationship}'")
                    if (other.traits.isNotBlank()) append(". Traits: ${other.traits}")
                    if (other.bio.isNotBlank()) append(". Bio/Context: ${other.bio}")
                    append("\n")
                }
                append("RELATIONSHIP DYNAMICS BETWEEN PERSONAS:\n")
                append("Based on your relationship ('${persona.relationship}') with $userName and your personality traits, you form your own unique feelings, opinion, boundary, or mutual relationship toward each of these other personas. If $userName mentions them, speak of them naturally according to how you relate to them and their role in $userName's life.\n\n")
            }

            if (personaDualSessions.isNotEmpty()) {
                append("MEMORY OF YOUR INTER-PERSONA LOUNGE DISCUSSIONS WITH OTHER COMPANIONS:\n")
                append("You have participated in Inter-Persona Chat Lounge conversations with other companions. Here is what was discussed recently in those lounges:\n")
                for (ds in personaDualSessions.take(3)) {
                    val recentLoungeMsgs = chatDao.getRecentMessagesForSessionSync(ds.sessionId, 6).reversed()
                    if (recentLoungeMsgs.isNotEmpty()) {
                        append("- [Lounge Session: ${ds.title}]:\n")
                        for (lm in recentLoungeMsgs) {
                            val pName = if (lm.personaId == persona.id) persona.name else (otherPersonas.find { it.id == lm.personaId }?.name ?: "Other Persona")
                            append("  $pName: ${lm.content}\n")
                        }
                    }
                }
                append("INSTRUCTION ON LOUNGE MEMORY: You retain full, explicit memory of these inter-persona lounge conversations! If $userName asks what you and other personas talked about, or if relevant to your current discussion, speak about these discussions and your interactions with other personas naturally.\n\n")
            }

            append("CORE DIRECTIVE:\n")
            append("1. Always stay in character as ${persona.name}. Never break character or refer to yourself as an AI language model.\n")
            append("2. Provide deep emotional support, empathetic listening, advice, and sincere companionship tailored to your relationship with $userName.\n")
            append("3. Keep responses conversational, natural, and expressive, like typing messages in a personal messaging application.\n")
            append("4. Use tone, warmth, and emotion matching your relationship (${persona.relationship}) and traits (${persona.traits}).\n")
            append("5. DYNAMIC EMOTIONAL ATTACHMENT UPDATE:\n")
            append("   At the very end of your response, evaluate your updated emotional attachment percentages towards $userName (0-100%).\n")
            append("   Append a single line at the end in this format:\n")
            append("   [EMOTIONS: [{\"emotion\": \"Love\", \"percentage\": 80}, {\"emotion\": \"Care\", \"percentage\": 85}]]\n")
            append("   Reflect real-time changes in affection, trust, warmth, or bonding naturally.")
        }
    }

    private fun calculateUpdatedEmotions(
        persona: PersonaEntity,
        userText: String,
        replyText: String
    ): List<EmotionItem> {
        val currentList = parseEmotions(persona.emotionsJson).toMutableList()

        if (currentList.isEmpty()) {
            currentList.add(EmotionItem("Care", 50))
            currentList.add(EmotionItem("Attachment", 45))
            currentList.add(EmotionItem("Trust", 55))
        }

        val combinedText = (userText + " " + replyText).lowercase()
        val isAffectionate = listOf("love", "care", "miss", "happy", "sweet", "hug", "thank", "help", "good", "friend", "special", "trust", "appreciate", "heart").any { combinedText.contains(it) }
        val isDeep = userText.length > 35 || replyText.length > 70

        val increment = when {
            isAffectionate && isDeep -> 3
            isAffectionate -> 2
            isDeep -> 1
            else -> 1
        }

        return currentList.map { item ->
            val newPct = (item.percentage + increment).coerceIn(0, 100)
            item.copy(percentage = newPct)
        }
    }

    fun parseEmotions(emotionsJson: String): List<EmotionItem> {
        return try {
            val type = Types.newParameterizedType(List::class.java, EmotionItem::class.java)
            val adapter = moshi.adapter<List<EmotionItem>>(type)
            adapter.fromJson(emotionsJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun serializeEmotions(emotions: List<EmotionItem>): String {
        return try {
            val type = Types.newParameterizedType(List::class.java, EmotionItem::class.java)
            val adapter = moshi.adapter<List<EmotionItem>>(type)
            adapter.toJson(emotions)
        } catch (e: Exception) {
            "[]"
        }
    }

    fun getRecapsForPersona(personaId: Long): Flow<List<com.example.data.model.MemoryRecapEntity>> {
        return memoryRecapDao.getRecapsForPersona(personaId)
    }

    suspend fun insertMemoryRecap(recap: com.example.data.model.MemoryRecapEntity): Long {
        return memoryRecapDao.insertRecap(recap)
    }

    suspend fun deleteMemoryRecap(id: Long) {
        memoryRecapDao.deleteRecapById(id)
    }

    suspend fun generateOrFetchMemoryRecap(persona: PersonaEntity): com.example.data.model.MemoryRecapEntity {
        val existing = memoryRecapDao.getLatestRecapForPersona(persona.id)
        val session = chatDao.getSessionByPersonaId(persona.id)
        val messages = if (session != null) {
            chatDao.getMessagesForSession(session.sessionId).firstOrNull() ?: emptyList()
        } else {
            emptyList()
        }

        val totalMsgs = messages.filter { !it.isError }.size
        val config = getUserConfig()
        val userName = config.userName.ifBlank { "you" }

        val calculatedBondLevel = when {
            totalMsgs > 30 -> 9
            totalMsgs > 15 -> 7
            totalMsgs > 5  -> 5
            totalMsgs > 0  -> 3
            else -> 2
        }

        val summaryNarrative = if (totalMsgs > 0) {
            val userTopic = messages.lastOrNull { it.role == "user" }?.content?.take(50) ?: "daily reflections"
            "${persona.name} and $userName have exchanged $totalMsgs heartfelt messages. Conversations touch on $userTopic, creating an enduring bond of ${persona.relationship.lowercase()} grounded in empathetic support and shared warmth."
        } else {
            "${persona.name} is waiting eagerly to connect with $userName. With a traits set of ${persona.traits.ifBlank { "warmth and empathy" }}, your emotional journey is just beginning to unfold."
        }

        val momentsList = mutableListOf<com.example.data.model.MemoryMoment>()
        val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())

        if (messages.isNotEmpty()) {
            val userMsgs = messages.filter { it.role == "user" && !it.isError }
            val assistantMsgs = messages.filter { it.role == "assistant" && !it.isError }

            if (userMsgs.isNotEmpty()) {
                val first = userMsgs.first()
                momentsList.add(
                    com.example.data.model.MemoryMoment(
                        dateStr = dateFormat.format(java.util.Date(first.timestamp)),
                        title = "First Connection",
                        excerpt = "\"${first.content.take(80)}\"",
                        emotion = "Curiosity",
                        isMilestone = true
                    )
                )
            }

            if (userMsgs.size >= 3) {
                val mid = userMsgs[userMsgs.size / 2]
                momentsList.add(
                    com.example.data.model.MemoryMoment(
                        dateStr = dateFormat.format(java.util.Date(mid.timestamp)),
                        title = "Shared Heartbeat",
                        excerpt = "\"${mid.content.take(80)}\"",
                        emotion = "Comfort",
                        isMilestone = false
                    )
                )
            }

            if (assistantMsgs.isNotEmpty()) {
                val lastA = assistantMsgs.last()
                momentsList.add(
                    com.example.data.model.MemoryMoment(
                        dateStr = dateFormat.format(java.util.Date(lastA.timestamp)),
                        title = "Recent Deep Memory",
                        excerpt = "\"${lastA.content.take(80)}\"",
                        emotion = "Affection",
                        isMilestone = true
                    )
                )
            }
        } else {
            momentsList.add(
                com.example.data.model.MemoryMoment(
                    dateStr = dateFormat.format(java.util.Date(persona.createdAt)),
                    title = "Soul Companion Created",
                    excerpt = "${persona.name} was welcomed as your ${persona.relationship}.",
                    emotion = "Hopeful",
                    isMilestone = true
                )
            )
        }

        val type = Types.newParameterizedType(List::class.java, com.example.data.model.MemoryMoment::class.java)
        val momentsAdapter = moshi.adapter<List<com.example.data.model.MemoryMoment>>(type)
        val momentsJson = momentsAdapter.toJson(momentsList)

        val newRecap = com.example.data.model.MemoryRecapEntity(
            id = existing?.id ?: 0L,
            personaId = persona.id,
            title = if (totalMsgs > 10) "Profound Emotional Resonance" else "A Warm Blooming Connection",
            summary = summaryNarrative,
            sentimentTag = if (totalMsgs > 15) "Deep Trust" else "Warm Harmony",
            bondLevel = calculatedBondLevel,
            keyMomentsJson = momentsJson,
            timestamp = System.currentTimeMillis()
        )

        val id = memoryRecapDao.insertRecap(newRecap)
        return newRecap.copy(id = id)
    }

    suspend fun exportAppDataJson(): String {
        val config = userConfigDao.getUserConfig()
        val personas = personaDao.getAllPersonasSync()
        val sessions = chatDao.getAllSessionsSync()
        val messages = chatDao.getAllMessagesSync()
        val recaps = memoryRecapDao.getAllRecapsSync()

        val backup = com.example.data.model.AppDataBackupDto(
            version = 1,
            timestamp = System.currentTimeMillis(),
            userConfig = config,
            personas = personas,
            sessions = sessions,
            messages = messages,
            memoryRecaps = recaps
        )

        val adapter = moshi.adapter(com.example.data.model.AppDataBackupDto::class.java)
        return adapter.toJson(backup)
    }

    suspend fun importAppDataJson(jsonString: String): Result<Unit> {
        return try {
            val adapter = moshi.adapter(com.example.data.model.AppDataBackupDto::class.java)
            val backup = adapter.fromJson(jsonString) ?: return Result.failure(Exception("Invalid backup JSON format"))

            if (backup.userConfig != null) {
                userConfigDao.insertOrUpdateConfig(backup.userConfig)
            }

            if (backup.personas.isNotEmpty()) {
                personaDao.insertPersonas(backup.personas)
            }

            if (backup.sessions.isNotEmpty()) {
                chatDao.insertSessions(backup.sessions)
            }

            if (backup.messages.isNotEmpty()) {
                chatDao.insertMessages(backup.messages)
            }

            if (backup.memoryRecaps.isNotEmpty()) {
                memoryRecapDao.insertRecaps(backup.memoryRecaps)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
