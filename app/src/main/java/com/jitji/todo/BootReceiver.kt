package com.jitji.todo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        val appContext = context.applicationContext
        LockscreenService.start(appContext)
        ServiceWatchdog.scheduleHeartbeat(appContext)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = TaskRepository(appContext)
                repo.allPending().forEach { task ->
                    ReminderScheduler.schedule(appContext, task)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
