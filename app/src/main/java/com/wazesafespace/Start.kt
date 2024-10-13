package com.wazesafespace

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Start : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)

        lifecycleScope.launch {
            delay((2  * 1000).toLong())
            finish()
            startActivity(Intent(this@Start, AuthActivity::class.java))
        }
    }

}