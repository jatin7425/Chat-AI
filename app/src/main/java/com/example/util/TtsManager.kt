package com.example.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

class TtsManager(context: Context) {
    private var tts: TextToSpeech? = null
    var isInitialized by mutableStateOf(false)
        private set
    var currentlySpeakingId by mutableStateOf<String?>(null)
        private set

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let { engine ->
                    engine.language = Locale.US
                    engine.setPitch(1.02f)
                    engine.setSpeechRate(0.96f)

                    try {
                        val voices = engine.voices
                        if (!voices.isNullOrEmpty()) {
                            val bestVoice = voices.firstOrNull { voice ->
                                voice.locale.language == "en" &&
                                        (voice.quality >= Voice.QUALITY_HIGH ||
                                                voice.name.lowercase().contains("network") ||
                                                voice.name.lowercase().contains("natural") ||
                                                voice.name.lowercase().contains("en-us-x"))
                            } ?: voices.firstOrNull { it.locale.language == "en" }
                            if (bestVoice != null) {
                                engine.voice = bestVoice
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            currentlySpeakingId = utteranceId
                        }

                        override fun onDone(utteranceId: String?) {
                            if (currentlySpeakingId == utteranceId) {
                                currentlySpeakingId = null
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            if (currentlySpeakingId == utteranceId) {
                                currentlySpeakingId = null
                            }
                        }
                    })

                    isInitialized = true
                }
            }
        }
    }

    fun speak(
        messageId: String,
        text: String,
        voiceName: String = "",
        gender: String = "Female",
        relationship: String = "",
        personaName: String = ""
    ) {
        val engine = tts ?: return

        if (currentlySpeakingId == messageId) {
            stop()
            return
        }

        stop()

        // Clean markdown/symbols from text for natural speech
        val cleanText = text
            .replace(Regex("\\*\\*|\\*|__|_|`"), "")
            .replace(Regex("#+\\s"), "")
            .replace(Regex("\\[(.*?)\\]\\(.*\\)"), "$1")
            .trim()

        if (cleanText.isBlank()) return

        // Select voice and pitch based on persona gender
        try {
            val gLower = gender.lowercase()
            val relLower = relationship.lowercase()

            val isMale = gLower.contains("male") || gLower.contains("boy") || gLower.contains("man") || gLower.contains("he/him") ||
                    relLower.contains("boyfriend") || relLower.contains("brother") || relLower.contains("husband") || relLower.contains("father")

            val isFemale = !isMale || gLower.contains("female") || gLower.contains("girl") || gLower.contains("woman") || gLower.contains("she") ||
                    relLower.contains("girlfriend") || relLower.contains("sister") || relLower.contains("wife") || relLower.contains("mother")

            val femaleIdentifiers = listOf(
                "female", "fem", "woman", "girl",
                "sfg", "sfc", "sfe", "sff",
                "tpf", "tpd", "tpc", "tpe",
                "iol", "iof", "iog",
                "-f-", "_f_", "f00", "f01", "f02", "f03"
            )

            val maleIdentifiers = listOf(
                "male", "man", "boy",
                "iom", "iob", "ioc", "iod",
                "sfa", "sfb", "tpa", "tpb",
                "-m-", "_m_", "m00", "m01", "m02", "m03"
            )

            val availableVoices = engine.voices
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
            }

            // Set pitch and speech rate for realistic gender timbre
            if (isFemale) {
                engine.setPitch(1.36f)
                engine.setSpeechRate(0.98f)
            } else {
                engine.setPitch(0.85f)
                engine.setSpeechRate(0.94f)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        currentlySpeakingId = messageId
        engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, messageId)
    }

    fun stop() {
        tts?.stop()
        currentlySpeakingId = null
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
