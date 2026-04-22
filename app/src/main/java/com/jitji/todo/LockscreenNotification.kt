package com.jitji.todo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object LockscreenNotification {

    const val CHANNEL_ID = "jitji_todo_lockscreen"
    const val NOTIFICATION_ID = 9001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "잠금화면 할일",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "잠금화면에 계속 표시되는 할일 목록"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)
    }

    fun build(context: Context, tasks: List<Task>): Notification {
        ensureChannel(context)

        val pending = tasks.filter { !it.isDone }
        val visible = pending.take(7)
        val body = if (visible.isEmpty()) {
            "할일을 추가해보세요"
        } else buildString {
            visible.forEachIndexed { idx, t ->
                append("• ")
                append(t.title)
                if (idx < visible.size - 1) append('\n')
            }
            if (pending.size > visible.size) {
                append("\n… +${pending.size - visible.size}")
            }
        }

        val title = if (pending.isEmpty()) "잊지마 할일"
        else "잊지마 할일 (${pending.size}개)"

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            context, 1, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(visible.firstOrNull()?.title ?: "할일을 추가해보세요")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(false)
            .setSilent(true)
            .setContentIntent(contentPi)
            .build()
    }

    fun update(context: Context, tasks: List<Task>) {
        NotificationManagerCompat.from(context)
            .notify(NOTIFICATION_ID, build(context, tasks))
    }
}
