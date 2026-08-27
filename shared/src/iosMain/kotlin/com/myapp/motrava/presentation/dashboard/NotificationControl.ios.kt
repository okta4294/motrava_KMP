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
            content.setTitle(if (isOverdue) "Service Overdue!" else "Service Reminder")
            content.setBody(if (isOverdue) {
                "Your vehicle is overdue for $serviceName!"
            } else {
                "Your vehicle is approaching $serviceName ($progressPercent%)."
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
