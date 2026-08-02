package com.example.data.spaces

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
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
