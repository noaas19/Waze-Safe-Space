package com.wazesafespace

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment

class Home : Fragment(R.layout.activity_home) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // הגדרת לחיצה על הכפתור מתוך הפרגמנט
        val newBtn: Button = view.findViewById(R.id.newBtn)
        view.findViewById<Button>(R.id.buttonFindSafeSpace)
            .setOnClickListener {
                val activity = requireActivity() as? MainActivity ?: return@setOnClickListener
                activity.findShelterManually()
            }
        newBtn.setOnClickListener {
            // החלפת פרגמנט מתוך הפרגמנט
            activity?.supportFragmentManager?.beginTransaction()
                ?.replace(R.id.fragmentContainer, CleanFragment())
                ?.commit()
        }
    }
}
