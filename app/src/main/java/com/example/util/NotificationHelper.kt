package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity

private const val CHANNEL_ID = "space_events_channel"

const val EXTRA_NOTIFICATION_TYPE = "notification_type"
const val EXTRA_SPACE_ID = "notification_space_id"
const val EXTRA_PERSONA_ID = "notification_persona_id"
const val EXTRA_GROUP_CHAT_ID = "notification_group_chat_id"

const val NOTIFICATION_TYPE_DIRECT_CHAT = "direct_chat"
const val NOTIFICATION_TYPE_GROUP_CHAT = "group_chat"

fun ensureNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val channel = NotificationChannel(
        CHANNEL_ID,
        "Space Events",
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        description = "Persona replies and simulation events from your Spaces"
    }
    context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
}

/**
 * Posts a notification that deep-links back into the specific chat it came from. The launcher
 * mipmap is used as the small icon rather than a purpose-built monochrome drawable -- Android
 * auto-masks it in the status bar, which is an acceptable stopgap until a dedicated icon asset
 * exists.
 */
fun postSpaceEventNotification(
    context: Context,
    notificationId: Int,
    title: String,
    body: String,
    type: String,
    spaceId: String,
    personaId: String?,
    groupChatId: String?
) {
    ensureNotificationChannel(context)

    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(EXTRA_NOTIFICATION_TYPE, type)
        putExtra(EXTRA_SPACE_ID, spaceId)
        personaId?.let { putExtra(EXTRA_PERSONA_ID, it) }
        groupChatId?.let { putExtra(EXTRA_GROUP_CHAT_ID, it) }
    }
    val pendingIntent = PendingIntent.getActivity(
        context,
        notificationId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(context.applicationInfo.icon)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()

    try {
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    } catch (_: SecurityException) {
        // User denied the POST_NOTIFICATIONS runtime permission -- nothing to do, the in-app
        // Firestore notifications record still exists for whenever they open the app.
    }
}
