package com.touchmouse

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class OverlayService : Service() {
    companion object { const val CH_ID = "touchmouse_fg" }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notif = NotificationCompat.Builder(this, CH_ID)
            .setContentTitle("TouchMouse actif")
            .setContentText("Souris virtuelle + Whisper Flow en cours")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
        startForeground(1, notif)
        // Délègue à l'accessibility service si dispo, sinon tente overlay direct via manager temporaire
        // L'AccessibilityService est le vrai owner de l'overlay ; ce service garde juste le foreground
        TouchMouseService.instance?.tryShowOverlayIfPermitted()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        TouchMouseService.instance?.tryShowOverlayIfPermitted()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Reste actif en tout temps : relance auto si swipe des recents
        val restart = Intent(applicationContext, OverlayService::class.java)
        restart.setPackage(packageName)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(restart)
            else
                startService(restart)
        } catch (_: Exception) {}
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            val ch = NotificationChannel(CH_ID, "TouchMouse", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }
}
