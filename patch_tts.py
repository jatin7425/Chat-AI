import sys

path = "app/src/main/java/com/example/util/TtsManager.kt"
with open(path, "r") as f:
    content = f.read()

# Replace the speak function signature and voice selection logic
search = """    fun speak(
        messageId: String,
        text: String,
        gender: String = "Female",
        relationship: String = "",
        personaName: String = ""
    ) {"""

replace = """    fun speak(
        messageId: String,
        text: String,
        voiceName: String = "",
        gender: String = "Female",
        relationship: String = "",
        personaName: String = ""
    ) {"""
content = content.replace(search, replace)

search_voice_logic = """            val availableVoices = engine.voices
            if (!availableVoices.isNullOrEmpty()) {
                val englishVoices = availableVoices.filter { it.locale.language == "en" }
                if (isFemale) {
                    val targetVoice = englishVoices.firstOrNull { voice ->
                        val name = voice.name.lowercase()
                        val features = voice.features.map { it.lowercase() }
                        femaleIdentifiers.any { name.contains(it) } || features.any { it.contains("female") }
                    } ?: englishVoices.firstOrNull { voice ->
                        val name = voice.name.lowercase()
                        !maleIdentifiers.any { name.contains(it) }
                    } ?: englishVoices.firstOrNull()
                    if (targetVoice != null) {
                        engine.voice = targetVoice
                    }
                } else {
                    val targetVoice = englishVoices.firstOrNull { voice ->
                        val name = voice.name.lowercase()
                        val features = voice.features.map { it.lowercase() }
                        maleIdentifiers.any { name.contains(it) } || features.any { it.contains("male") }
                    } ?: englishVoices.firstOrNull()
                    if (targetVoice != null) {
                        engine.voice = targetVoice
                    }
                }
            }"""

replace_voice_logic = """            val availableVoices = engine.voices
            if (!availableVoices.isNullOrEmpty()) {
                var targetVoice: android.speech.tts.Voice? = null
                
                // Try to find the exact voice matching voiceName (with emotion)
                if (voiceName.isNotBlank()) {
                    targetVoice = availableVoices.firstOrNull { it.name.equals(voiceName, ignoreCase = true) }
                    // Fallback to base voice if emotion variant not found
                    if (targetVoice == null) {
                        val baseVoice = com.example.util.VoiceEmotions.getBaseVoice(voiceName)
                        targetVoice = availableVoices.firstOrNull { it.name.equals(baseVoice, ignoreCase = true) }
                    }
                }
                
                // Fallback to gender/relationship logic
                if (targetVoice == null) {
                    val englishVoices = availableVoices.filter { it.locale.language == "en" }
                    if (isFemale) {
                        targetVoice = englishVoices.firstOrNull { voice ->
                            val name = voice.name.lowercase()
                            val features = voice.features.map { it.lowercase() }
                            femaleIdentifiers.any { name.contains(it) } || features.any { it.contains("female") }
                        } ?: englishVoices.firstOrNull { voice ->
                            val name = voice.name.lowercase()
                            !maleIdentifiers.any { name.contains(it) }
                        } ?: englishVoices.firstOrNull()
                    } else {
                        targetVoice = englishVoices.firstOrNull { voice ->
                            val name = voice.name.lowercase()
                            val features = voice.features.map { it.lowercase() }
                            maleIdentifiers.any { name.contains(it) } || features.any { it.contains("male") }
                        } ?: englishVoices.firstOrNull()
                    }
                }
                
                if (targetVoice != null) {
                    engine.voice = targetVoice
                }
            }"""

content = content.replace(search_voice_logic, replace_voice_logic)

with open(path, "w") as f:
    f.write(content)
