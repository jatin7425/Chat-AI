package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "personas")
data class PersonaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val age: Int = 25,
    val gender: String = "Female",
    val relationship: String = "Friend",
    val avatarStyle: String = "Avataaars (Modern)",
    val avatarSeed: String = "",
    val avatarUri: String? = null,

    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val avatarBlob: ByteArray? = null,

    val traits: String = "", // e.g., "Caring, Sarcastic, Wealthy"
    val eyeColor: String = "",
    val hairStyle: String = "",
    val height: String = "",
    val build: String = "",
    val clothingStyle: String = "",
    val keyFeatures: String = "",
    val bio: String = "",
    val customChatBgUri: String? = null,

    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val customChatBgBlob: ByteArray? = null,

    val customChatBgOpacity: Float = 0.8f, // Range 0.0f to 1.0f

    val emotionsJson: String = "[]", // JSON array of EmotionItem
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PersonaEntity

        if (id != other.id) return false
        if (name != other.name) return false
        if (age != other.age) return false
        if (gender != other.gender) return false
        if (relationship != other.relationship) return false
        if (avatarStyle != other.avatarStyle) return false
        if (avatarSeed != other.avatarSeed) return false
        if (avatarUri != other.avatarUri) return false
        if (avatarBlob != null) {
            if (other.avatarBlob == null) return false
            if (!avatarBlob.contentEquals(other.avatarBlob)) return false
        } else if (other.avatarBlob != null) return false
        if (traits != other.traits) return false
        if (eyeColor != other.eyeColor) return false
        if (hairStyle != other.hairStyle) return false
        if (height != other.height) return false
        if (build != other.build) return false
        if (clothingStyle != other.clothingStyle) return false
        if (keyFeatures != other.keyFeatures) return false
        if (bio != other.bio) return false
        if (customChatBgUri != other.customChatBgUri) return false
        if (customChatBgBlob != null) {
            if (other.customChatBgBlob == null) return false
            if (!customChatBgBlob.contentEquals(other.customChatBgBlob)) return false
        } else if (other.customChatBgBlob != null) return false
        if (customChatBgOpacity != other.customChatBgOpacity) return false
        if (emotionsJson != other.emotionsJson) return false
        if (isDefault != other.isDefault) return false
        if (createdAt != other.createdAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + age
        result = 31 * result + gender.hashCode()
        result = 31 * result + relationship.hashCode()
        result = 31 * result + avatarStyle.hashCode()
        result = 31 * result + avatarSeed.hashCode()
        result = 31 * result + (avatarUri?.hashCode() ?: 0)
        result = 31 * result + (avatarBlob?.contentHashCode() ?: 0)
        result = 31 * result + traits.hashCode()
        result = 31 * result + eyeColor.hashCode()
        result = 31 * result + hairStyle.hashCode()
        result = 31 * result + height.hashCode()
        result = 31 * result + build.hashCode()
        result = 31 * result + clothingStyle.hashCode()
        result = 31 * result + keyFeatures.hashCode()
        result = 31 * result + bio.hashCode()
        result = 31 * result + (customChatBgUri?.hashCode() ?: 0)
        result = 31 * result + (customChatBgBlob?.contentHashCode() ?: 0)
        result = 31 * result + customChatBgOpacity.hashCode()
        result = 31 * result + emotionsJson.hashCode()
        result = 31 * result + isDefault.hashCode()
        result = 31 * result + createdAt.hashCode()
        return result
    }
}
