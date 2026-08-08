package com.example.lolipet.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.lolipet.Prefs

/** 开机自启 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED && Prefs.autoStart) {
            PetService.start(context)
        }
    }
}
