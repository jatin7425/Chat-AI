package com.example.data.spaces

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Talks directly to the user's own LiteLLM proxy / self-hosted OpenAI-compatible server (not
 * the SoulAI backend) purely to list available models for the picker in Settings. Plain OkHttp
 * rather than Retrofit, matching SpacesApiClient's style, since this is one read-only endpoint.
 * The backend is what actually calls this server for chat completions (Phase 4), reading the
 * saved baseUrl/model from Firestore users/{uid} -- this client never sends a chat message.
 */
object LiteLlmDirectClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun sanitizeBaseUrl(rawUrl: String): String {
        var clean = rawUrl.trim()
        if (clean.endsWith("/")) clean = clean.dropLast(1)
        return clean
    }

    /** GET {baseUrl}/v1/models, OpenAI-compatible response shape: {"data": [{"id": "..."}]}. */
    suspend fun fetchModels(baseUrl: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${sanitizeBaseUrl(baseUrl)}/v1/models")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("HTTP ${response.code}"))
                }
                val bodyText = response.body?.string().orEmpty()
                val json = JSONObject(bodyText)
                val dataArray = json.optJSONArray("data")
                val models = buildList {
                    if (dataArray != null) {
                        for (i in 0 until dataArray.length()) {
                            val id = dataArray.optJSONObject(i)?.optString("id")
                            if (!id.isNullOrBlank()) add(id)
                        }
                    }
                }
                Result.success(models)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
