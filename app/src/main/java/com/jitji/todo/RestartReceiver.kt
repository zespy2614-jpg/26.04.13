package com.jitji.todo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        runCatching {
            LockscreenService.start(context.applicationContext)
        }
    }

    companion object {
        const val ACTION_RESTART = "com.jitji.todo.ACTION_RESTART_SERVICE"
    }
}
