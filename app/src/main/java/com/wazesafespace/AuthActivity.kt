package com.wazesafespace

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.wazesafespace.databinding.ActivityAuthBinding

class AuthActivity : AppCompatActivity() {


    private lateinit var binding : ActivityAuthBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        FirebaseAuth.getInstance()
            .addAuthStateListener { authState ->
                if(authState.currentUser != null) {
                    finish()
                    startActivity(Intent(this, MainActivity::class.java))
                }
            }
    }



}