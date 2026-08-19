package com.myapp.motrava.presentation.dashboard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import org.koin.core.context.GlobalContext

actual fun sendServiceReminderNotification(
    serviceName: String,
    progressPercent: Int,
    isOverdue: Boolean
) {
    val context = GlobalContext.get().get<Context>()
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            "service_reminders",
            "Service Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for vehicle service reminders"
        }
        notificationManager.createNotificationChannel(channel)
    }

    val title = if (isOverdue) "Service Overdue!" else "Service Reminder"
    val text = if (isOverdue) {
        "Your vehicle is overdue for $serviceName!"
    } else {
        "Your vehicle is approaching $serviceName ($progressPercent%)."
    }

    val notification = NotificationCompat.Builder(context, "service_reminders")
        .setSmallIcon(android.R.drawable.ic_dialog_alert) // Fallback icon
        .setContentTitle(title)
        .setContentText(text)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()

    notificationManager.notify(serviceName.hashCode(), notification)
}
