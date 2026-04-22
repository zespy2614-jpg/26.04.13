package com.jitji.todo

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer

class LockscreenService : Service() {

    private lateinit var tasksLive: LiveData<List<Task>>
    private val observer = Observer<List<Task>> { list ->
        LockscreenNotification.update(this, list)
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_SCREEN_ON) return
            val i = Intent(ctx, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            runCatching { ctx.startActivity(i) }
        }
    }
    private var screenReceiverRegistered = false

    override fun onCreate() {
        super.onCreate()
        LockscreenNotification.ensureChannel(this)
        val initial = LockscreenNotification.build(this, emptyList())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                LockscreenNotification.NOTIFICATION_ID,
                initial,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(LockscreenNotification.NOTIFICATION_ID, initial)
        }

        tasksLive = TaskRepository(applicationContext).observeAll()
        tasksLive.observeForever(observer)

        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        screenReceiverRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        ServiceWatchdog.scheduleImmediateRestart(applicationContext)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        if (::tasksLive.isInitialized) {
            tasksLive.removeObserver(observer)
        }
        if (screenReceiverRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            screenReceiverRegistered = false
        }
        ServiceWatchdog.scheduleImmediateRestart(applicationContext)
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, LockscreenService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
