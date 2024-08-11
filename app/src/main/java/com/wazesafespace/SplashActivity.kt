package com.wazesafespace

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash) // ודא שהשם נכון

        val buttonFindSafeSpace = findViewById<Button>(R.id.buttonFindSafeSpace)
        buttonFindSafeSpace.setOnClickListener {
            // כאשר לוחצים על הכפתור, עוברים ל-MainActivity
            val intent = Intent(this@SplashActivity, MainActivity::class.java)
            startActivity(intent)
            finish() // מסיים את ה-SplashActivity
        }
    }
}
