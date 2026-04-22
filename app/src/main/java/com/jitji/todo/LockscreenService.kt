package com.jitji.todo

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer

class LockscreenService : Service() {

    private lateinit var tasksLive: LiveData<List<Task>>
    private val observer = Observer<List<Task>> { list ->
        LockscreenNotification.update(this, list)
    }

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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartIntent = Intent(applicationContext, LockscreenService::class.java)
        startService(restartIntent)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        if (::tasksLive.isInitialized) {
            tasksLive.removeObserver(observer)
        }
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
