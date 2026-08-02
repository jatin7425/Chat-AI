package com.example.data.spaces

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Client for the local/dev-tunnel backend (see backend/). Mirrors LiteLlmApi.kt's
 * dynamic-base-URL pattern (base = UserConfigEntity.spacesApiBaseUrl, the dev-tunnel URL) but
 * uses plain OkHttp rather than Retrofit since there's currently just a couple of endpoints --
 * this grows into a fuller Retrofit-based client in later phases (direct chat, Story Feed).
 */
object SpacesApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // LLM completions can legitimately take much longer than the other (fast) backend calls.
    private val chatClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun sanitizeBaseUrl(rawUrl: String): String {
        var clean = rawUrl.trim()
        if (clean.endsWith("/")) clean = clean.dropLast(1)
        return clean
    }

    /**
     * Resolves the URL that should actually be used: a persisted Settings override
     * (UserConfigEntity.spacesApiBaseUrl) if the user has set one, otherwise the build-time
     * default -- the local dev-tunnel URL baked into debug builds, or the CI-supplied URL baked
     * into release builds (see app/build.gradle.kts' BACKEND_BASE_URL buildConfigField).
     */
    fun effectiveBaseUrl(persistedUrl: String): String {
        return persistedUrl.ifBlank { BuildConfig.BACKEND_BASE_URL }
    }

    suspend fun healthCheck(baseUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${sanitizeBaseUrl(baseUrl)}/api/health")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("Health check failed: HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Upserts the signed-in user's profile in the backend's Firestore users/{uid} collection. */
    suspend fun syncUser(baseUrl: String, idToken: String, displayName: String?): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val bodyJson = JSONObject().apply {
                    put("displayName", displayName ?: JSONObject.NULL)
                }.toString()

                val request = Request.Builder()
                    .url("${sanitizeBaseUrl(baseUrl)}/api/users/sync")
                    .addHeader("Authorization", "Bearer $idToken")
                    .post(bodyJson.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Result.success(Unit)
                    } else {
                        Result.failure(IllegalStateException("User sync failed: HTTP ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Deletes a space and all its subcollections via the backend's Admin-SDK recursiveDelete -- firestore.rules denies client-side space deletes entirely. */
    suspend fun deleteSpace(baseUrl: String, idToken: String, spaceId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${sanitizeBaseUrl(baseUrl)}/api/spaces/$spaceId")
                    .addHeader("Authorization", "Bearer $idToken")
                    .delete()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Result.success(Unit)
                    } else {
                        Result.failure(IllegalStateException("Delete space failed: HTTP ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Starts a Space's simulation via the backend (not a direct Firestore write) so it can backscan existing chats for unfulfilled commitments and queue them before ticking begins. */
    suspend fun startSimulation(baseUrl: String, idToken: String, spaceId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${sanitizeBaseUrl(baseUrl)}/api/spaces/$spaceId/start")
                    .addHeader("Authorization", "Bearer $idToken")
                    .post("{}".toRequestBody(jsonMediaType))
                    .build()

                // Uses chatClient's longer timeout -- the backscan runs an LLM commitment check
                // per persona before responding, which can take a while for spaces with many.
                chatClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Result.success(Unit)
                    } else {
                        val bodyText = response.body?.string().orEmpty()
                        val errorMsg = runCatching { JSONObject(bodyText).optString("error") }.getOrNull()
                        Result.failure(IllegalStateException(errorMsg?.ifBlank { null } ?: "Start simulation failed: HTTP ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Fire-and-forget: tells the backend the user just opened this Space, so it can (debounced) generate a spontaneous activity beat if the Space has been idle a while. */
    suspend fun notifySpaceView(baseUrl: String, idToken: String, spaceId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${sanitizeBaseUrl(baseUrl)}/api/spaces/$spaceId/view")
                    .addHeader("Authorization", "Bearer $idToken")
                    .post("{}".toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Result.success(Unit)
                    } else {
                        Result.failure(IllegalStateException("Notify space view failed: HTTP ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Registers this device's FCM token so push notifications for persona replies/simulation events can reach it. */
    suspend fun registerFcmToken(baseUrl: String, idToken: String, token: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val bodyJson = JSONObject().apply { put("token", token) }.toString()
                val request = Request.Builder()
                    .url("${sanitizeBaseUrl(baseUrl)}/api/users/fcm-token")
                    .addHeader("Authorization", "Bearer $idToken")
                    .post(bodyJson.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Result.success(Unit)
                    } else {
                        Result.failure(IllegalStateException("Register FCM token failed: HTTP ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Dismisses a "needs your input" card that was never meant to be a real persona (e.g. a role reference like "the executive"), closing out the matching blocked Story Feed task too. */
    suspend fun dismissNeedsInput(
        baseUrl: String,
        idToken: String,
        spaceId: String,
        requestId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${sanitizeBaseUrl(baseUrl)}/api/spaces/$spaceId/needs-input/$requestId/dismiss")
                .addHeader("Authorization", "Bearer $idToken")
                .post("{}".toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    val bodyText = response.body?.string().orEmpty()
                    val errorMsg = runCatching { JSONObject(bodyText).optString("error") }.getOrNull()
                    Result.failure(IllegalStateException(errorMsg?.ifBlank { null } ?: "Dismiss failed: HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Sends a direct-chat message to a persona; the backend writes both the user message and the assistant reply to Firestore, returning the reply text too. */
    suspend fun sendDirectMessage(
        baseUrl: String,
        idToken: String,
        spaceId: String,
        personaId: String,
        text: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bodyJson = JSONObject().apply { put("text", text) }.toString()

            val request = Request.Builder()
                .url("${sanitizeBaseUrl(baseUrl)}/api/spaces/$spaceId/personas/$personaId/messages")
                .addHeader("Authorization", "Bearer $idToken")
                .post(bodyJson.toRequestBody(jsonMediaType))
                .build()

            chatClient.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errorMsg = runCatching { JSONObject(bodyText).optString("error") }.getOrNull()
                    return@withContext Result.failure(
                        IllegalStateException(errorMsg?.ifBlank { null } ?: "Send failed: HTTP ${response.code}")
                    )
                }
                Result.success(JSONObject(bodyText).optString("reply"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Sends a message in a group chat; the backend has every member persona reply in turn, each seeing the others' replies from this same round. */
    suspend fun sendGroupMessage(
        baseUrl: String,
        idToken: String,
        spaceId: String,
        groupChatId: String,
        text: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val bodyJson = JSONObject().apply { put("text", text) }.toString()

            val request = Request.Builder()
                .url("${sanitizeBaseUrl(baseUrl)}/api/spaces/$spaceId/group-chats/$groupChatId/messages")
                .addHeader("Authorization", "Bearer $idToken")
                .post(bodyJson.toRequestBody(jsonMediaType))
                .build()

            chatClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    val bodyText = response.body?.string().orEmpty()
                    val errorMsg = runCatching { JSONObject(bodyText).optString("error") }.getOrNull()
                    Result.failure(IllegalStateException(errorMsg?.ifBlank { null } ?: "Send failed: HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Regenerates the persona's reply to the last (un-replied-to) user message -- for when the reply itself failed, without re-sending the message and duplicating it. */
    suspend fun retryLastMessage(
        baseUrl: String,
        idToken: String,
        spaceId: String,
        personaId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${sanitizeBaseUrl(baseUrl)}/api/spaces/$spaceId/personas/$personaId/messages/retry")
                .addHeader("Authorization", "Bearer $idToken")
                .post("{}".toRequestBody(jsonMediaType))
                .build()

            chatClient.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errorMsg = runCatching { JSONObject(bodyText).optString("error") }.getOrNull()
                    return@withContext Result.failure(
                        IllegalStateException(errorMsg?.ifBlank { null } ?: "Retry failed: HTTP ${response.code}")
                    )
                }
                Result.success(JSONObject(bodyText).optString("reply"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Moves a persona to a Place; the backend updates the persona doc and logs the move to their activity log. */
    suspend fun movePersonaToPlace(
        baseUrl: String,
        idToken: String,
        spaceId: String,
        personaId: String,
        placeId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val bodyJson = JSONObject().apply { put("placeId", placeId) }.toString()

            val request = Request.Builder()
                .url("${sanitizeBaseUrl(baseUrl)}/api/spaces/$spaceId/personas/$personaId/move")
                .addHeader("Authorization", "Bearer $idToken")
                .post(bodyJson.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    val bodyText = response.body?.string().orEmpty()
                    val errorMsg = runCatching { JSONObject(bodyText).optString("error") }.getOrNull()
                    Result.failure(IllegalStateException(errorMsg?.ifBlank { null } ?: "Move failed: HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Uploads a persona's pfp/chat-background photo; the backend stores it in Cloudflare R2 and returns a public URL to save on the persona doc. */
    suspend fun uploadPersonaImage(
        baseUrl: String,
        idToken: String,
        spaceId: String,
        personaId: String,
        kind: String,
        bytes: ByteArray,
        mimeType: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestBody = bytes.toRequestBody(mimeType.toMediaType())
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", "$kind.jpg", requestBody)
                .build()

            val request = Request.Builder()
                .url("${sanitizeBaseUrl(baseUrl)}/api/spaces/$spaceId/personas/$personaId/images/$kind")
                .addHeader("Authorization", "Bearer $idToken")
                .post(multipart)
                .build()

            chatClient.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errorMsg = runCatching { JSONObject(bodyText).optString("error") }.getOrNull()
                    return@withContext Result.failure(
                        IllegalStateException(errorMsg?.ifBlank { null } ?: "Upload failed: HTTP ${response.code}")
                    )
                }
                Result.success(JSONObject(bodyText).optString("url"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Sends a photo to the backend's vision-model call, returning auto-detected appearance fields. */
    suspend fun analyzePersonaPhoto(
        baseUrl: String,
        idToken: String,
        spaceId: String,
        imageBase64: String,
        mimeType: String
    ): Result<AppearanceFieldsDto> = withContext(Dispatchers.IO) {
        try {
            val bodyJson = JSONObject().apply {
                put("imageBase64", imageBase64)
                put("mimeType", mimeType)
            }.toString()

            val request = Request.Builder()
                .url("${sanitizeBaseUrl(baseUrl)}/api/spaces/$spaceId/personas/analyze-photo")
                .addHeader("Authorization", "Bearer $idToken")
                .post(bodyJson.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Photo analysis failed: HTTP ${response.code}"))
                }
                val json = JSONObject(bodyText)
                Result.success(
                    AppearanceFieldsDto(
                        hairColor = json.optString("hairColor", ""),
                        hairStyle = json.optString("hairStyle", ""),
                        eyeColor = json.optString("eyeColor", ""),
                        skinTone = json.optString("skinTone", ""),
                        build = json.optString("build", ""),
                        height = json.optString("height", ""),
                        extraFeatures = json.optString("extraFeatures", "")
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class AppearanceFieldsDto(
    val hairColor: String,
    val hairStyle: String,
    val eyeColor: String,
    val skinTone: String,
    val build: String,
    val height: String,
    val extraFeatures: String
)
