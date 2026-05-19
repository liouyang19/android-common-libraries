package com.taisau.android.common.utils.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationUtils {

    private const val DEFAULT_CHANNEL = "default_channel"

    fun createChannel(
        context: Context,
        channelId: String = DEFAULT_CHANNEL,
        channelName: String,
        channelDescription: String = "",
        importance: Int = NotificationManager.IMPORTANCE_DEFAULT,
        enableVibration: Boolean = true,
        vibrationPattern: LongArray? = null,
        enableLights: Boolean = true,
        channelLightColor: Int = Color.BLUE,
        soundUri: Uri? = null,
        bypassDnd: Boolean = false,
        visibility: Int = NotificationCompat.VISIBILITY_PRIVATE
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(channelId)
        if (existing != null) return
        val channel = NotificationChannel(channelId, channelName, importance).apply {
            description = channelDescription
            enableVibration(enableVibration)
            vibrationPattern?.let { setVibrationPattern(it) }
            enableLights(enableLights)
            lightColor = channelLightColor
            setBypassDnd(bypassDnd)
            soundUri?.let { uri ->
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setSound(uri, attrs)
            }
        }
        manager.createNotificationChannel(channel)
    }

    fun deleteChannel(context: Context, channelId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.deleteNotificationChannel(channelId)
    }

    fun getChannel(context: Context, channelId: String): NotificationChannel? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.getNotificationChannel(channelId)
    }

    fun getNotificationManager(context: Context): NotificationManager {
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    fun sendNotification(
        context: Context,
        id: Int,
        channelId: String = DEFAULT_CHANNEL,
        title: String,
        content: String,
        smallIcon: Int,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT,
        largeIcon: Bitmap? = null,
        autoCancel: Boolean = true,
        contentIntent: PendingIntent? = null,
        deleteIntent: PendingIntent? = null,
        ticker: String? = null,
        whenMillis: Long = System.currentTimeMillis(),
        ongoing: Boolean = false,
        category: String? = null,
        group: String? = null,
        timeoutMs: Long? = null,
        progress: Pair<Int, Int>? = null,
        actions: List<NotificationCompat.Action> = emptyList(),
        extrasBuilder: (NotificationCompat.Builder.() -> Unit)? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel(context, channelId, channelName = title)
        }
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(priority)
            .setAutoCancel(autoCancel)
            .setWhen(whenMillis)
            .setOngoing(ongoing)
            .apply { largeIcon?.let { setLargeIcon(it) } }
            .apply { contentIntent?.let { setContentIntent(it) } }
            .apply { deleteIntent?.let { setDeleteIntent(it) } }
            .apply { ticker?.let { setTicker(it) } }
            .apply { timeoutMs?.let { setTimeoutAfter(it) } }
            .apply { category?.let { setCategory(it) } }
            .apply { group?.let { setGroup(it) } }
            .apply {
                progress?.let { (current, max) ->
                    setProgress(max, current, false)
                }
            }
            .apply { actions.forEach { addAction(it) } }
            .apply { extrasBuilder?.invoke(this) }
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    fun sendProgressNotification(
        context: Context,
        id: Int,
        channelId: String = DEFAULT_CHANNEL,
        title: String,
        content: String,
        smallIcon: Int,
        max: Int,
        current: Int,
        indeterminate: Boolean = false,
        priority: Int = NotificationCompat.PRIORITY_LOW,
        ongoing: Boolean = true
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel(context, channelId, channelName = title,
                importance = NotificationManager.IMPORTANCE_LOW)
        }
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(priority)
            .setOngoing(ongoing)
            .setProgress(max, current, indeterminate)
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    fun cancelNotification(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    fun cancelAllNotifications(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }

    fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun openNotificationSettings(context: Context) {
        val intent = Intent().apply {
            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    data class ChannelConfig(
        val id: String = DEFAULT_CHANNEL,
        val name: String = "General",
        val description: String = "",
        val importance: Int = NotificationManager.IMPORTANCE_DEFAULT,
        val enableVibration: Boolean = true,
        val vibrationPattern: LongArray? = null,
        val enableLights: Boolean = true,
        val channelLightColor: Int = Color.BLUE,
        val soundUri: Uri? = null,
        val bypassDnd: Boolean = false,
        val visibility: Int = NotificationCompat.VISIBILITY_PRIVATE
    )

    fun createChannels(context: Context, vararg channels: ChannelConfig) {
        channels.forEach { config ->
            createChannel(
                context = context,
                channelId = config.id,
                channelName = config.name,
                channelDescription = config.description,
                importance = config.importance,
                enableVibration = config.enableVibration,
                vibrationPattern = config.vibrationPattern,
                enableLights = config.enableLights,
                channelLightColor = config.channelLightColor,
                soundUri = config.soundUri,
                bypassDnd = config.bypassDnd,
                visibility = config.visibility
            )
        }
    }

    fun createBigTextNotification(
        context: Context,
        id: Int,
        channelId: String = DEFAULT_CHANNEL,
        title: String,
        content: String,
        bigText: String,
        smallIcon: Int,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT,
        autoCancel: Boolean = true,
        contentIntent: PendingIntent? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel(context, channelId, channelName = title)
        }
        val style = NotificationCompat.BigTextStyle()
            .bigText(bigText)
            .setBigContentTitle(title)
            .setSummaryText(content)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(style)
            .setPriority(priority)
            .setAutoCancel(autoCancel)
            .apply { contentIntent?.let { setContentIntent(it) } }
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    fun createInboxStyleNotification(
        context: Context,
        id: Int,
        channelId: String = DEFAULT_CHANNEL,
        title: String,
        content: String,
        lines: List<String>,
        smallIcon: Int,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT,
        autoCancel: Boolean = true,
        contentIntent: PendingIntent? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel(context, channelId, channelName = title)
        }
        val style = NotificationCompat.InboxStyle()
        style.setBigContentTitle(title)
        style.setSummaryText(content)
        for (line in lines) { style.addLine(line) }
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(style)
            .setPriority(priority)
            .setAutoCancel(autoCancel)
            .apply { contentIntent?.let { setContentIntent(it) } }
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    fun createMessagingStyleNotification(
        context: Context,
        id: Int,
        channelId: String = DEFAULT_CHANNEL,
        title: String,
        conversationTitle: String,
        messages: List<Pair<String, CharSequence>>,
        smallIcon: Int,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT,
        autoCancel: Boolean = true,
        contentIntent: PendingIntent? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel(context, channelId, channelName = title)
        }
        val style = NotificationCompat.MessagingStyle(conversationTitle)
        for ((sender, text) in messages) {
            style.addMessage(text, System.currentTimeMillis(), sender)
        }
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setStyle(style)
            .setPriority(priority)
            .setAutoCancel(autoCancel)
            .apply { contentIntent?.let { setContentIntent(it) } }
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }
}
