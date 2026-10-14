package com.focs.app.block

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the app blocker after the phone reboots or Focs is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            BlockerService.startIfReady(context)
        }
    }
}
