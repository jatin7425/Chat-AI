package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class NvidiaTtsManager(private val context: Context) {

    var currentlySpeakingId by mutableStateOf<String?>(null)
        private set

    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun speak(
        messageId: String,
        text: String,
        voiceName: String
    ) {
        if (currentlySpeakingId == messageId) {
            stop()
            return
        }
        stop()

        val cleanText = text
            .replace(Regex("\\[(.*?)\\]"), "") // Remove emotion tags like [Happy]
            .replace(Regex("\\*\\*|\\*|__|_|`"), "")
            .replace(Regex("#+\\s"), "")
            .trim()

        if (cleanText.isBlank()) return

        currentlySpeakingId = messageId

        scope.launch {
            try {
                // Determine emotion from the original text if possible
                var selectedVoice = voiceName.ifBlank { "Magpie-Multilingual.HI-IN.Jason" }
                selectedVoice = com.example.util.VoiceEmotions.getBaseVoice(selectedVoice)
                
                var emotionToUse = "Neutral"
                val match = Regex("\\[(.*?)\\]").find(text)
                if (match != null) {
                    val emotion = match.groupValues[1].replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                    val supportedEmotions = com.example.util.VoiceEmotions.getSupportedEmotions(selectedVoice)
                    if (supportedEmotions.contains(emotion)) {
                        emotionToUse = emotion
                    }
                }
                selectedVoice = if (selectedVoice.contains("HouZhen") || selectedVoice.contains("Siwei")) selectedVoice else "$selectedVoice.$emotionToUse"
                
                // Force EN-US voice to prevent stuttering/hallucination on English text
                selectedVoice = selectedVoice.replace(".HI-IN.", ".EN-US.")

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("text", cleanText)
                    .addFormDataPart("language", "hi-IN") // default to hi-IN based on user prompt, or derive from voice
                    .addFormDataPart("voice", selectedVoice)
                    .addFormDataPart("encoding", "LINEAR_PCM")
                    .addFormDataPart("sample_rate_hz", "44100")
                    .build()

                val request = Request.Builder()
                    .url("https://877104f7-e885-42b9-8de8-f6e4c6303969.invocation.api.nvcf.nvidia.com/v1/audio/synthesize")
                    .addHeader("Authorization", "Bearer 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")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    response.body?.byteStream()?.let { inputStream ->
                        val tempFile = File(context.cacheDir, "tts_temp_$messageId.wav")
                        val pcmBytes = inputStream.readBytes()
                        
                        // Add WAV header for 44.1kHz 16-bit Mono PCM
                        val sampleRate = 44100
                        val channels = 1
                        val bitDepth = 16
                        
                        val totalAudioLen = pcmBytes.size
                        val totalDataLen = totalAudioLen + 36
                        val byteRate = sampleRate * channels * (bitDepth / 8)
                        
                        val header = ByteArray(44)
                        header[0] = 'R'.code.toByte()
                        header[1] = 'I'.code.toByte()
                        header[2] = 'F'.code.toByte()
                        header[3] = 'F'.code.toByte()
                        header[4] = (totalDataLen and 0xff).toByte()
                        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
                        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
                        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
                        header[8] = 'W'.code.toByte()
                        header[9] = 'A'.code.toByte()
                        header[10] = 'V'.code.toByte()
                        header[11] = 'E'.code.toByte()
                        header[12] = 'f'.code.toByte()
                        header[13] = 'm'.code.toByte()
                        header[14] = 't'.code.toByte()
                        header[15] = ' '.code.toByte()
                        header[16] = 16
                        header[17] = 0
                        header[18] = 0
                        header[19] = 0
                        header[20] = 1
                        header[21] = 0
                        header[22] = channels.toByte()
                        header[23] = 0
                        header[24] = (sampleRate and 0xff).toByte()
                        header[25] = ((sampleRate shr 8) and 0xff).toByte()
                        header[26] = ((sampleRate shr 16) and 0xff).toByte()
                        header[27] = ((sampleRate shr 24) and 0xff).toByte()
                        header[28] = (byteRate and 0xff).toByte()
                        header[29] = ((byteRate shr 8) and 0xff).toByte()
                        header[30] = ((byteRate shr 16) and 0xff).toByte()
                        header[31] = ((byteRate shr 24) and 0xff).toByte()
                        header[32] = (channels * (bitDepth / 8)).toByte()
                        header[33] = 0
                        header[34] = bitDepth.toByte()
                        header[35] = 0
                        header[36] = 'd'.code.toByte()
                        header[37] = 'a'.code.toByte()
                        header[38] = 't'.code.toByte()
                        header[39] = 'a'.code.toByte()
                        header[40] = (totalAudioLen and 0xff).toByte()
                        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
                        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
                        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

                        FileOutputStream(tempFile).use { output ->
                            output.write(header)
                            output.write(pcmBytes)
                        }
                        
                        // Play back on main thread
                        launch(Dispatchers.Main) {
                            if (currentlySpeakingId == messageId) {
                                mediaPlayer?.release()
                                mediaPlayer = MediaPlayer().apply {
                                    setAudioAttributes(
                                        AudioAttributes.Builder()
                                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                            .setUsage(AudioAttributes.USAGE_MEDIA)
                                            .build()
                                    )
                                    setDataSource(tempFile.absolutePath)
                                    setOnCompletionListener {
                                        if (currentlySpeakingId == messageId) {
                                            currentlySpeakingId = null
                                        }
                                    }
                                    prepare()
                                    start()
                                }
                            }
                        }
                    }
                } else {
                    launch(Dispatchers.Main) {
                        currentlySpeakingId = null
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    if (currentlySpeakingId == messageId) {
                        currentlySpeakingId = null
                    }
                }
            }
        }
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {}
        mediaPlayer = null
        currentlySpeakingId = null
    }

    fun release() {
        stop()
    }
}
