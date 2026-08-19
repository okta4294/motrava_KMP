package com.myapp.motrava.presentation.dashboard

import platform.UserNotifications.*

actual fun sendServiceReminderNotification(
    serviceName: String,
    progressPercent: Int,
    isOverdue: Boolean
) {
    val center = UNUserNotificationCenter.currentNotificationCenter()
    center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { granted, _ ->
        if (granted) {
            val content = UNMutableNotificationContent()
            content.setTitle("Motrava Service Reminder")
            content.setBody(if (isOverdue) {
                "Peringatan: $serviceName sudah lewat batas servis!"
            } else {
                "Info: $serviceName sudah mencapai $progressPercent% dari batas servis."
            })
            content.setSound(UNNotificationSound.defaultSound)
            
            val request = UNNotificationRequest.requestWithIdentifier(
                identifier = "reminder_${serviceName.hashCode()}",
                content = content,
                trigger = null
            )
            
            center.addNotificationRequest(request) { error ->
                if (error != null) {
                    println("Error sending notification: ${error.localizedDescription}")
                }
            }
        }
    }
}
