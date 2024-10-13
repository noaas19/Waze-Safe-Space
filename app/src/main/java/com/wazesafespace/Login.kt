package com.wazesafespace

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class Login : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.login, container, false)

        // init Firebase Auth & Firestore
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        val emailEditText = view.findViewById<EditText>(R.id.emailEditText)
        val passwordEditText = view.findViewById<EditText>(R.id.passwordEditText)
        val loginButton = view.findViewById<Button>(R.id.loginButton)
        val errorTextView = view.findViewById<TextView>(R.id.errorTextView)
        val errorCard = view.findViewById<CardView>(R.id.errorCard)

        val toLogin = view.findViewById<Button>(R.id.noAccount)
        toLogin.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }


        loginButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            //findNavController().navigate(R.id.action_menuLogin_to_menuProfile)
                        } else {

                            val message = if(task.exception?.message!=null &&
                                task.exception!!.message!!.contains("INVALID_LOGIN")) {
                                "Incorrect email or password"
                            }
                            else {
                                task.exception?.message
                            }
                            errorTextView.text = "Authentication failed: ${message}"
                            errorTextView.visibility = TextView.VISIBLE
                            errorCard.visibility = TextView.VISIBLE

                        }
                    }
            } else {
                Toast.makeText(requireContext(), "Please enter email and password", Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }
}
