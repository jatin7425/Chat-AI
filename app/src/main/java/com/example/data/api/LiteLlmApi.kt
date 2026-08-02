package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class ModelItemDto(
    @Json(name = "id") val id: String,
    @Json(name = "object") val objectType: String? = "model",
    @Json(name = "created") val created: Long? = null,
    @Json(name = "owned_by") val ownedBy: String? = null
)

@JsonClass(generateAdapter = true)
data class ModelsResponseDto(
    @Json(name = "data") val data: List<ModelItemDto>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class ChatMessageDto(
    @Json(name = "role") val role: String,
    @Json(name = "content") val content: String
)

@JsonClass(generateAdapter = true)
data class ChatCompletionRequestDto(
    @Json(name = "model") val model: String,
    @Json(name = "messages") val messages: List<ChatMessageDto>,
    @Json(name = "temperature") val temperature: Double? = 0.7,
    @Json(name = "max_tokens") val maxTokens: Int? = 1000
)

@JsonClass(generateAdapter = true)
data class ChoiceMessageDto(
    @Json(name = "role") val role: String? = "assistant",
    @Json(name = "content") val content: String? = ""
)

@JsonClass(generateAdapter = true)
data class ChatChoiceDto(
    @Json(name = "index") val index: Int? = 0,
    @Json(name = "message") val message: ChoiceMessageDto?,
    @Json(name = "finish_reason") val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class ChatCompletionResponseDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "choices") val choices: List<ChatChoiceDto>? = emptyList()
)

interface LiteLlmApiService {
    @GET
    suspend fun getModels(@Url fullUrl: String): ModelsResponseDto

    @POST
    suspend fun createChatCompletion(
        @Url fullUrl: String,
        @Body request: ChatCompletionRequestDto
    ): ChatCompletionResponseDto
}

object LiteLlmClient {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    val apiService: LiteLlmApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://localhost/") // Base URL placeholder; @Url overrides it dynamically
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(LiteLlmApiService::class.java)
    }

    fun sanitizeBaseUrl(rawUrl: String): String {
        var clean = rawUrl.trim()
        if (clean.endsWith("/")) {
            clean = clean.dropLast(1)
        }
        return clean
    }

    fun buildModelsUrl(baseUrl: String): String {
        val clean = sanitizeBaseUrl(baseUrl)
        return "$clean/v1/models"
    }

    fun buildChatUrl(baseUrl: String): String {
        val clean = sanitizeBaseUrl(baseUrl)
        return "$clean/v1/chat/completions"
    }
}
