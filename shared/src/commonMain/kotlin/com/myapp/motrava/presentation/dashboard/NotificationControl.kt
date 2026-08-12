package com.myapp.motrava.presentation.dashboard

expect fun sendServiceReminderNotification(
    serviceName: String,
    progressPercent: Int,
    isOverdue: Boolean
)
