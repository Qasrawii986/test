package com.smsexpense.tracker.service.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.smsexpense.tracker.MainActivity
import com.smsexpense.tracker.R
import com.smsexpense.tracker.domain.model.Category
import com.smsexpense.tracker.domain.model.Payment
import com.smsexpense.tracker.service.bubble.BubbleBlocker
import com.smsexpense.tracker.ui.components.formatAmount
import com.smsexpense.tracker.util.AppLocale
import com.smsexpense.tracker.util.AppLog

/**
 * Fallback for when the floating bubble cannot be shown (overlay permission
 * revoked — which happens on every reinstall — bubble disabled, or the system
 * refused the foreground-service start), and when a bubble auto-hides without
 * being categorized. A payment must never be silently dropped on the floor.
 *
 * The notification carries one-tap actions for the first few categories so the
 * fast path survives even without the bubble.
 */
object PaymentNotifier {

    const val CHANNEL_ID = "payment_alerts"
    private const val MAX_ACTIONS = 3

    fun notifyUncategorized(
        context: Context,
        payment: Payment,
        categories: List<Category>,
        blocker: BubbleBlocker = BubbleBlocker.NONE,
    ) {
        if (!canPost(context)) {
            AppLog.d("Notification permission missing; cannot surface payment ${payment.id}")
            return
        }
        createChannel(context)

        val title = formatAmount(payment.amount, payment.currency) +
            (payment.merchant?.let { " • $it" } ?: "")
        val localized = com.smsexpense.tracker.util.AppLocale.wrap(context)
        val text = when (blocker) {
            BubbleBlocker.NONE -> localized.getString(R.string.notif_tap_to_categorize)
            else -> blocker.message(localized)
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            payment.id.toInt(),
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_PAYMENT_ID, payment.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

        categories.take(MAX_ACTIONS).forEach { category ->
            builder.addAction(
                NotificationCompat.Action.Builder(
                    0,
                    "${category.icon} ${category.name}",
                    CategorizeActionReceiver.pendingIntent(context, payment.id, category.id),
                ).build()
            )
        }

        post(context, payment.id, builder.build())
    }

    fun cancel(context: Context, paymentId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(paymentId))
    }

    private fun post(context: Context, paymentId: Long, notification: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(notificationId(paymentId), notification)
        } catch (e: SecurityException) {
            AppLog.e("Notification blocked", e)
        }
    }

    /** Stable per-payment id so re-notifying replaces instead of stacking. */
    private fun notificationId(paymentId: Long): Int = (paymentId % Int.MAX_VALUE).toInt() + 2000

    fun canPost(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun createChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val localized = AppLocale.wrap(context)
        val channel = NotificationChannel(
            CHANNEL_ID,
            localized.getString(R.string.bubble_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = localized.getString(R.string.bubble_channel_desc) }
        manager.createNotificationChannel(channel)
    }
}
