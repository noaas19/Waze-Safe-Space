package com.wazesafespace

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues.TAG
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class MyForegroundService : Service() {

    private lateinit var database: DatabaseReference


    companion object {
        var isServiceRunning = false
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        isServiceRunning = true

        database = FirebaseDatabase.getInstance().reference
        // Reference to the alerts node
        val alertsRef = database.child("alerts")

        // Listen for real-time updates
        alertsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val alertData = snapshot.value as? Map<String, Any>
                val alertsList = alertData?.get("alerts") as? List<Map<String, Any>>

                if (alertsList != null) {
                    val beerShevaAlert = alertsList.any { alert ->
                        val cities = alert["cities"] as? String
                        cities?.contains("באר שבע") == true
                    }

                    if (beerShevaAlert) {
                        Log.d(TAG, "Alert for Beer Sheva found")
                        //כאן אמורים לשלוח התראות וכשמשתמש ילחץ על התראה -צריך להפעיל פונקציה FindShelterHandler
                        sendBeerShevaAlertNotification()

                    } else {
                        Log.d(TAG, "No alert for Beer Sheva found, no route will be shown.")
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error fetching data: ", error.toException())
            }
        })

        // יצירת ערוץ ההתראה (Notification Channel)
        createNotificationChannel()

        // יצירת ההתראה
        val notificationIntent = Intent(this, MapFragment::class.java)
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
    private fun sendBeerShevaAlertNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = "CHANNEL_ID"

        // כוונה שתיפתח כאשר המשתמש ילחץ על ההתראה
        val intent = Intent(this, MapFragment::class.java).apply {
            putExtra("action", "guideUserByLocation") // מעבירים פרמטר שמעיד על הפעולה
        }

        // PendingIntent עם FLAG_UPDATE_CURRENT כדי לוודא שהכוונה מתעדכנת
        /*val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )*/
        val broadcastIntent = Intent("com.example.ACTION_SEND_MESSAGE")
        broadcastIntent.putExtra("message", "התקבלה התראה לבאר שבע, לחץ כאן לקבלת הנחיות.")

// Create a PendingIntent that sends the broadcast
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            broadcastIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )


        // בניית ההתראה
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("התראה לבאר שבע")
            .setContentText("התקבלה התראה לבאר שבע, לחץ כאן לקבלת הנחיות.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // ההתראה תעלם אחרי לחיצה
            .build()

        // הצגת ההתראה
        notificationManager.notify(2, notification)
    }


    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        Log.d("MyForegroundService", "Foreground service is destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}