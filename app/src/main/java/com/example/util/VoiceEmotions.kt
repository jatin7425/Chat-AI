package com.example.util

/**
 * Placeholder emotion catalog — the original definition was missing from the repo (no
 * VoiceEmotions.kt existed anywhere despite being referenced from SoulRepository/
 * CreatePersonaSheet/NvidiaTtsManager). This reconstructs the API contract inferred from
 * those call sites. The supported-emotion list is a uniform guess, not sourced from NVIDIA's
 * actual Magpie-Multilingual docs — correct it if you have the real per-voice catalog.
 */
object VoiceEmotions {
    private val defaultSupportedEmotions = listOf(
        "Neutral", "Happy", "Sad", "Angry", "Surprised", "Fearful"
    )

    /** Strips a trailing ".<Emotion>" suffix, e.g. "Magpie-Multilingual.HI-IN.Jason.Happy" -> "Magpie-Multilingual.HI-IN.Jason". */
    fun getBaseVoice(voiceName: String): String {
        if (voiceName.isBlank()) return voiceName
        val parts = voiceName.split(".")
        return if (parts.size > 3) parts.take(3).joinToString(".") else voiceName
    }

    fun getSupportedEmotions(baseVoice: String): List<String> = defaultSupportedEmotions
}
