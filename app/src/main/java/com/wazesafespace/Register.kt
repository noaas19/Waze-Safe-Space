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

class Register : Fragment(R.layout.register) {

    private lateinit var auth: FirebaseAuth
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var registerButton: Button

    private lateinit var errorTextView : TextView
    private lateinit var errorCard : CardView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // אתחול FirebaseAuth
        auth = FirebaseAuth.getInstance()

        val toLogin = view.findViewById<Button>(R.id.alreadyHaveAccount)
        toLogin.setOnClickListener {
            findNavController().popBackStack()
        }
        // אתחול רכיבי ממשק
        emailEditText = view.findViewById(R.id.emailEditText)
        passwordEditText = view.findViewById(R.id.editTextPassword)
        registerButton = view.findViewById(R.id.buttonRegister)
         errorTextView = view.findViewById<TextView>(R.id.errorTextView)
         errorCard = view.findViewById<CardView>(R.id.errorCard)
        // מאזין ללחיצה על כפתור ההרשמה
        registerButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                registerUser(email, password)
            } else {
                Toast.makeText(activity, "נא למלא את כל השדות", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun registerUser(email: String, password: String) {
        MainActivity.showTipDialog = true
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val userId = task.result.user?.uid ?:return@addOnCompleteListener
                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(userId)
                        .set(User(
                            email=email,
                            accessibility="ללא מגבלת נגישות"
                        ))
                    // המשתמש נרשם בהצלחה
                    Toast.makeText(activity, "נרשמת בהצלחה!", Toast.LENGTH_SHORT).show()

                } else {
                    // שגיאה בהרשמה
                    task.exception?.let {
                        errorTextView.text = "Registration failed: ${it.message}"
                        errorTextView.visibility = TextView.VISIBLE
                        errorCard.visibility = TextView.VISIBLE
                        Toast.makeText(activity, "שגיאה בהרשמה: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
    }
}