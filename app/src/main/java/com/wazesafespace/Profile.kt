package com.wazesafespace

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.skydoves.powerspinner.PowerSpinnerView

class Profile : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var firstNameEditText: EditText
    private lateinit var lastNameEditText: EditText
    private lateinit var emailEditText: EditText
    private lateinit var birthYearEditText: EditText
    private lateinit var accessibilitySpinner: PowerSpinnerView
    private lateinit var saveButton: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.profile, container, false)

        // init Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Locating interface elements
        firstNameEditText = view.findViewById(R.id.editTextFirstName)
        lastNameEditText = view.findViewById(R.id.editTextLastName)
        emailEditText = view.findViewById(R.id.editTextEmail)
        birthYearEditText = view.findViewById(R.id.editTextBirthYear)
        accessibilitySpinner = view.findViewById(R.id.spinnerAccessibility)
        saveButton = view.findViewById(R.id.buttonSave)

        loadUserData()

        saveButton.setOnClickListener {
            saveUserData()
        }

        return view
    }


    private fun loadUserData() {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            firestore.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        // Loading data to the interface
                        firstNameEditText.setText(document.getString("firstName"))
                        lastNameEditText.setText(document.getString("lastName"))
                        emailEditText.setText(document.getString("email"))
                        birthYearEditText.setText(document.getString("birthYear"))

                        val accessibility = document.getString("accessibility")
                        val accessibilityIndex =
                            resources.getStringArray(R.array.accessibility_options)
                                .indexOf(accessibility)
                        accessibilitySpinner.selectItemByIndex(accessibilityIndex)
                    }
                }
        }
    }

    private fun saveUserData() {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            val user = hashMapOf(
                "firstName" to firstNameEditText.text.toString(),
                "lastName" to lastNameEditText.text.toString(),
                "email" to emailEditText.text.toString(),
                "birthYear" to birthYearEditText.text.toString(),
                "accessibility" to resources.getStringArray(R.array.accessibility_options)[accessibilitySpinner.selectedIndex]
            )

            firestore.collection("users")
                .document(userId)
                .set(user)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "השינויים נשמרו בהצלחה!", Toast.LENGTH_SHORT)
                        .show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(
                        requireContext(),
                        "שגיאה בשמירת הנתונים: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }
}
