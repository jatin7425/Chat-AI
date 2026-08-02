package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.SecondaryPink
import kotlin.math.abs

@Composable
fun AvatarView(
    name: String,
    avatarStyle: String = "Avataaars (Modern)",
    avatarSeed: String = "",
    avatarUri: String? = null,
    avatarBlob: ByteArray? = null,
    size: Dp = 56.dp,
    showBorder: Boolean = true,
    modifier: Modifier = Modifier
) {
    val seed = avatarSeed.ifBlank { name }
    val gradientColors = getGradientForName(seed)

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (showBorder) {
                    Modifier.border(
                        width = (size.value * 0.04f).dp.coerceAtLeast(1.5.dp),
                        brush = Brush.linearGradient(listOf(PrimaryPurple, SecondaryPink)),
                        shape = CircleShape
                    )
                } else Modifier
            )
            .padding((size.value * 0.04f).dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(gradientColors)),
        contentAlignment = Alignment.Center
    ) {
        val imageModel: Any? = avatarBlob ?: avatarUri?.ifBlank { null }
        if (imageModel != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageModel)
                    .crossfade(true)
                    .build(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Render stylized initials / vector avatar based on seed or style
            val initials = if (name.isNotBlank()) {
                val parts = name.trim().split(" ")
                if (parts.size >= 2) "${parts[0].first()}${parts[1].first()}".uppercase()
                else name.take(2).uppercase()
            } else "AI"

            val icon = getIconForStyle(avatarStyle)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (size >= 64.dp) {
                    Icon(
                        imageVector = icon,
                        contentDescription = name,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(size * 0.4f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = initials,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.35f).sp
                )
            }
        }
    }
}

private fun String?.isNullByBlank(): Boolean = this == null || this.isBlank()

private fun getGradientForName(seed: String): List<Color> {
    val hash = abs(seed.hashCode())
    val palettes = listOf(
        listOf(Color(0xFF6A11CB), Color(0xFF2575FC)),
        listOf(Color(0xFFFF0844), Color(0xFFFFB199)),
        listOf(Color(0xFFF12711), Color(0xFFF5AF19)),
        listOf(Color(0xFF8A2387), Color(0xFFE94057), Color(0xFFF27121)),
        listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
        listOf(Color(0xFF1F1C2C), Color(0xFF928DAB)),
        listOf(Color(0xFF00B4DB), Color(0xFF0083B0))
    )
    return palettes[hash % palettes.size]
}

private fun getIconForStyle(style: String): ImageVector {
    return when {
        style.contains("Realistic", ignoreCase = true) -> Icons.Default.Person
        style.contains("Counselor", ignoreCase = true) || style.contains("Pixel", ignoreCase = true) -> Icons.Default.Psychology
        style.contains("Cartoon", ignoreCase = true) -> Icons.Default.Face
        else -> Icons.Default.Favorite
    }
}
