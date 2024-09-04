package com.wazesafespace

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

class MyForegroundService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // יצירת ערוץ ההתראה (Notification Channel)
        createNotificationChannel()

        // יצירת ההתראה
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, "CHANNEL_ID")
            .setContentTitle("Service Running")
            .setContentText("Your app is running in the background")
            .setSmallIcon(R.drawable.ic_launcher_foreground)  //לייבא אייקון מתאים
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        // הפעלת השירות כ-Foreground Service
        startForeground(1, notification)
        Log.d("MyForegroundService", "Notification created and service started")

        return START_STICKY
    }

    // פונקציה ליצירת ערוץ ההתראה
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "CHANNEL_ID",
                "Service Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
