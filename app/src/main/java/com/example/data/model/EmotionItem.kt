package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class EmotionItem(
    val emotion: String,
    val percentage: Int
)
