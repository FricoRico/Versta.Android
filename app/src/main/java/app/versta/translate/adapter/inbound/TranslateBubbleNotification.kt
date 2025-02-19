package app.versta.translate.adapter.inbound

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import app.versta.translate.BubbleActivity
import app.versta.translate.MainActivity
import app.versta.translate.MainApplication.Companion.TRANSLATION_BUBBLE_SHORTCUT_ID
import app.versta.translate.MainApplication.Companion.TRANSLATION_NOTIFICATION_CHANNEL_ID
import app.versta.translate.MainApplication.Companion.TRANSLATION_NOTIFICATION_ID
import app.versta.translate.R
import kotlin.random.Random


object TranslateBubbleNotification : TranslateNotification {
    private const val name = "Translation Bubbles"
    private const val descriptionText = "Allows you to translate selected text in a bubble overlay"
    private val importance = NotificationManager.IMPORTANCE_MIN

    /**
     * Registers the notification channel for the translation bubble.
     */
    fun registerForActivity(activity: ComponentActivity) {
        val notificationManager =
            activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel =
            NotificationChannel(TRANSLATION_NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Clears the translation notification.
     */
    fun clearNotification(context: Context) {
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(TRANSLATION_NOTIFICATION_ID)
    }

    /**
     * Checks if the user has enabled notifications.
     */
    private fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU) {
            return false
        }

        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Launches the main activity with the given text to be translated.
     */
    private fun launchMainActivity(context: Context, text: CharSequence) {
        val mainActivity = Intent(context, MainActivity::class.java).apply {
            putExtra("input", text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.startActivity(mainActivity)
    }

    /**
     * Shows a notification with the given text to be translated. If the user has disabled
     * notifications or the notification permission, the main activity is launched instead.
     */
    @SuppressLint("MissingPermission", "NewApi")
    override fun showNotification(context: Context, text: CharSequence) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (!notificationManager.areNotificationsEnabled() || !hasNotificationPermission(context)) {
            launchMainActivity(context, text)
            return
        }

        val updateIntent = Intent(TRANSLATION_NOTIFICATION_CHANNEL_ID).apply {
            putExtra("input", text)
        }
        context.sendBroadcast(updateIntent)

        val bubbleActivity = Intent(context, BubbleActivity::class.java).apply {
            putExtra("input", text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val bubbleIntent = PendingIntent.getActivity(
            context,
            TRANSLATION_NOTIFICATION_ID,
            bubbleActivity,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val mainActivity = Intent(context, MainActivity::class.java).apply {
            putExtra("input", text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val mainIntent = PendingIntent.getActivity(
            context,
            TRANSLATION_NOTIFICATION_ID,
            mainActivity,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val bubbleData = Notification.BubbleMetadata.Builder(
            bubbleIntent,
            Icon.createWithResource(context, R.mipmap.ic_launcher)
        )
            .setDesiredHeightResId(R.dimen.bubble_height)
            .setAutoExpandBubble(true)
            .setSuppressNotification(true)
            .build()

        val person = Person.Builder()
            .setImportant(true)
            .setBot(true)
            .setName(context.getString(R.string.app_name))
            .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
            .build()

        val builder = Notification.Builder(context, TRANSLATION_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("Translate")
            .setContentText(text)
            .setGroup(TRANSLATION_NOTIFICATION_CHANNEL_ID)
            .setShortcutId(TRANSLATION_BUBBLE_SHORTCUT_ID)
            .setBadgeIconType(Notification.BADGE_ICON_LARGE)
            .setContentIntent(mainIntent)
            .setStyle(
                Notification.MessagingStyle(person)
                    .addMessage(text, System.currentTimeMillis(), person)
            )
            .setBubbleMetadata(bubbleData)

        notificationManager.notify(TRANSLATION_NOTIFICATION_ID, builder.build())
    }
}