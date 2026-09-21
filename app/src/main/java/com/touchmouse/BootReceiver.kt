package com.touchmouse

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Redémarre la souris après reboot / mise à jour.
 * Garantit fonctionnement en tout temps.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val a = intent?.action ?: return
        if (a == Intent.ACTION_BOOT_COMPLETED ||
            a == Intent.ACTION_MY_PACKAGE_REPLACED ||
            a == Intent.ACTION_PACKAGE_REPLACED ||
            a == "android.intent.action.QUICKBOOT_POWERON") {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val prefs = context.prefsFlow().first()
                    // Ne relance que si l'utilisateur avait activé l'overlay
                    if (prefs.overlayEnabled) {
                        val i = Intent(context, OverlayService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                            context.startForegroundService(i)
                        else
                            context.startService(i)
                    }
                } catch (_: Exception) {}
            }
        }
    }
}
