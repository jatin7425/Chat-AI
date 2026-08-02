import sys

path = "app/src/main/java/com/example/data/repository/SoulRepository.kt"
with open(path, "r") as f:
    content = f.read()

# For generatePersonaReply
search_1 = """            var cleanedReply = rawReply
            var updatedEmotions: List<EmotionItem>? = null"""

replace_1 = """            var cleanedReply = rawReply
            var updatedEmotions: List<EmotionItem>? = null
            
            var detectedVoiceEmotion = ""
            val voiceEmotionRegex = Regex(""" + '"""' + """^\\[(.*?)\\]\\s*""" + '"""' + """)
            val voiceMatch = voiceEmotionRegex.find(cleanedReply.trim())
            if (voiceMatch != null) {
                detectedVoiceEmotion = voiceMatch.groupValues[1]
                cleanedReply = cleanedReply.replaceFirst(voiceMatch.value, "").trim()
            }"""

content = content.replace(search_1, replace_1)

# Now we need to update the voice in the DB.
# Right after:
#            if (!updatedEmotions.isNullOrEmpty()) {
#                val updatedJson = serializeEmotions(updatedEmotions)
#                val latestPersona = personaDao.getPersonaById(persona.id) ?: persona
#                if (latestPersona.emotionsJson != updatedJson) {
#                    personaDao.updatePersona(latestPersona.copy(emotionsJson = updatedJson))
#                }
#            }
search_2 = """            if (!updatedEmotions.isNullOrEmpty()) {
                val updatedJson = serializeEmotions(updatedEmotions)
                val latestPersona = personaDao.getPersonaById(persona.id) ?: persona
                if (latestPersona.emotionsJson != updatedJson) {
                    personaDao.updatePersona(latestPersona.copy(emotionsJson = updatedJson))
                }
            }"""

replace_2 = """            var latestPersona = personaDao.getPersonaById(persona.id) ?: persona
            var needsUpdate = false
            if (!updatedEmotions.isNullOrEmpty()) {
                val updatedJson = serializeEmotions(updatedEmotions)
                if (latestPersona.emotionsJson != updatedJson) {
                    latestPersona = latestPersona.copy(emotionsJson = updatedJson)
                    needsUpdate = true
                }
            }
            if (detectedVoiceEmotion.isNotEmpty()) {
                val baseVoice = com.example.util.VoiceEmotions.getBaseVoice(latestPersona.voice ?: "")
                // Validate if it's a supported emotion
                val supported = com.example.util.VoiceEmotions.getSupportedEmotions(baseVoice)
                val cleanEmotion = detectedVoiceEmotion.trim()
                if (supported.contains(cleanEmotion)) {
                    val newVoice = "$baseVoice.$cleanEmotion"
                    if (latestPersona.voice != newVoice) {
                        latestPersona = latestPersona.copy(voice = newVoice)
                        needsUpdate = true
                    }
                }
            }
            if (needsUpdate) {
                personaDao.updatePersona(latestPersona)
            }"""
            
content = content.replace(search_2, replace_2)

# Now for generateDualPersonaReply
search_3 = """            var cleanedReply = rawReply.trim()
            val prefix = "${speaker.name}:"
            if (cleanedReply.startsWith(prefix, ignoreCase = true)) {
                cleanedReply = cleanedReply.substring(prefix.length).trim()
            }"""

replace_3 = """            var cleanedReply = rawReply.trim()
            
            var detectedVoiceEmotion = ""
            val voiceEmotionRegex = Regex(""" + '"""' + """^\\[(.*?)\\]\\s*""" + '"""' + """)
            val voiceMatch = voiceEmotionRegex.find(cleanedReply)
            if (voiceMatch != null) {
                detectedVoiceEmotion = voiceMatch.groupValues[1]
                cleanedReply = cleanedReply.replaceFirst(voiceMatch.value, "").trim()
            }
            
            val prefix = "${speaker.name}:"
            if (cleanedReply.startsWith(prefix, ignoreCase = true)) {
                cleanedReply = cleanedReply.substring(prefix.length).trim()
            }
            
            if (detectedVoiceEmotion.isNotEmpty()) {
                var latestSpeaker = personaDao.getPersonaById(speaker.id) ?: speaker
                val baseVoice = com.example.util.VoiceEmotions.getBaseVoice(latestSpeaker.voice ?: "")
                val supported = com.example.util.VoiceEmotions.getSupportedEmotions(baseVoice)
                val cleanEmotion = detectedVoiceEmotion.trim()
                if (supported.contains(cleanEmotion)) {
                    val newVoice = "$baseVoice.$cleanEmotion"
                    if (latestSpeaker.voice != newVoice) {
                        personaDao.updatePersona(latestSpeaker.copy(voice = newVoice))
                    }
                }
            }"""

content = content.replace(search_3, replace_3)

with open(path, "w") as f:
    f.write(content)
