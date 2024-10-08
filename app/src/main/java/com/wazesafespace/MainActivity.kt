package com.wazesafespace

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import com.wazesafespace.databinding.ActivityMain2Binding
import java.util.UUID

sealed class CurrentScreen {
    data object Home : CurrentScreen()
    data object Register: CurrentScreen()
    data object Login : CurrentScreen()
    data object Map : CurrentScreen()
    data object Profile: CurrentScreen()
    data object MyShelters: CurrentScreen()
}

class MainActivity : AppCompatActivity() {
    private val NOTIFICATION_PERMISSION_CODE = 1001
    private val FINE_PERMISSION_CODE = 1
    lateinit var binding: ActivityMain2Binding
    private lateinit var alertReceiver: BroadcastReceiver
    private var currentScreen : CurrentScreen = CurrentScreen.Home
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {  // API 33
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_CODE)
            }
        }
    }


    private fun startForegroundService() {
        startForegroundService(Intent(this, MyForegroundService::class.java))
        Log.d("MainActivity","startForegroundService called")
    }

    private fun showBackgroundRunDialog() {
        Log.d("showBackgroundRunDialog", "showBackgroundRunDialog")
        AlertDialog.Builder(this)
            .setTitle("הפעלת ריצה ברקע")
            .setMessage("האפליקציה תרוץ ברקע כדי להמשיך לעקוב אחרי המיקום שלך ולוודא שאתה מקבל את כל ההתרעות בזמן אמת.")
            .setPositiveButton("אישור") { _, _ ->
                // הפעלת השירות לאחר שהמשתמש נתן אישור
                startForegroundService()

                // שמירת האישור ב-SharedPreferences כדי שלא נבקש שוב
                val sharedPreferences = getSharedPreferences("app_preferences", MODE_PRIVATE)
                with(sharedPreferences.edit()) {
                    putBoolean("isBackgroundApproved", true)
                    apply()
                }
            }
            .setNegativeButton("ביטול") { dialog, _ ->
                dialog.dismiss()
                // כאן אפשר להפסיק כל פעולה נוספת אם המשתמש סירב
            }
            .show()
    }



    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        findViewById<FragmentContainerView>(R.id.fragmentContainerMap).visibility = View.GONE

        when(item.itemId) {
            R.id.menuHome -> {
                if(currentScreen is CurrentScreen.Home) {
                    return true
                }
                currentScreen = CurrentScreen.Home

                replaceFragment(Home())
            }
            R.id.menuShelters -> {
                if(currentScreen is CurrentScreen.MyShelters) {
                    return true
                }
                currentScreen = CurrentScreen.MyShelters
                replaceFragment(Shelters())
            }
            R.id.menuLogout-> {

                AlertDialog
                    .Builder(this)
                    .setTitle("Log out")
                    .setMessage("Are you sure you want to log out?")
                    .setPositiveButton("Yes") { p0, p1 ->
                        FirebaseAuth.getInstance().signOut()
                        finish()
                        startActivity(Intent(this, AuthActivity::class.java))
                    }
                    .setNegativeButton("Cancel",null)
                    .show()
            }
            R.id.menuProfile -> {
                if(currentScreen is CurrentScreen.Profile) {
                    return true
                }
                currentScreen = CurrentScreen.Profile
                replaceFragment(Profile())
            }
        }

        return super.onOptionsItemSelected(item)
    }



    fun findShelterManually(fromManualAddress: String? = null) {
        val intent = Intent(this, MapFragment::class.java)
        val gson = Gson()
        intent.putExtra("event", gson.toJson(ShelterEvent(
            type="ShelterManually",
            address = fromManualAddress,
            currentLocation = true
        )))
        startActivity(intent)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMain2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.menuBtn.setOnClickListener {
            val popUpMenu = PopupMenu(this@MainActivity, it)
            popUpMenu.inflate(R.menu.menu_nav)
            popUpMenu.setOnMenuItemClickListener(::onOptionsItemSelected)
            popUpMenu.show()
        }
        // טוענים את הפרגמנט הראשוני עם הלוגו והכפתורים
        replaceFragment(Home())

        requestNotificationPermission()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                FINE_PERMISSION_CODE
            )
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
            ||ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.FOREGROUND_SERVICE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.FOREGROUND_SERVICE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION),
                FINE_PERMISSION_CODE
            )
            return
        }
        else {
            requestForegroundService()
        }



    }

    fun requestForegroundService() {
        val sharedPreferences = getSharedPreferences("app_preferences", MODE_PRIVATE)
        val isBackgroundApproved = sharedPreferences.getBoolean("isBackgroundApproved", false)

        // בדיקת האם המשתמש אישר כבר את הריצה ברקע
        if (isBackgroundApproved) {
            Log.d("isBackgroundApproved","isBackgroundApproved")
            // נוודא שהשירות לא פועל ואז נפעיל אותו
            if (!MyForegroundService.isServiceRunning) {
                startForegroundService()
                Log.d("MapFragment", "Starting Foreground Service")
            } else {
                Log.d("MapFragment", "Foreground service is already running")
            }
        } else {
            // המשתמש לא אישר ריצה ברקע, מבקשים אישור
            showBackgroundRunDialog()
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.fragmentContainer, fragment)
        fragmentTransaction.commit()
        isInHome = fragment is Home
    }

    var isInHome = true
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
    FINE_PERMISSION_CODE -> {

                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    requestForegroundService()
                    Log.d("Permission", "Location permission granted.")
                } else {
                    Log.d("Permission", "Location permission is denied.")
//                    Toast.makeText(
//                        this,
//                        "Location permission is denied, please allow the permission",
//                        Toast.LENGTH_SHORT
//                    ).show()
                }
            }
            NOTIFICATION_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // הרשאת התראות ניתנה
                    Log.d("NotificationPermission", "Notification permission granted.")
                } else {
                    // הרשאת התראות לא ניתנה
                    Log.d("NotificationPermission", "Notification permission is denied.")
                    Toast.makeText(
                        this,
                        "Notification permission is denied.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onBackPressed() {
        if(!isInHome) {
            currentScreen = CurrentScreen.Home
            replaceFragment(Home())
        } else {
            super.onBackPressed()
        }
    }
}
