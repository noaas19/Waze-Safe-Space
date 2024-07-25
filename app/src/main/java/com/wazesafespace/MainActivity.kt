package com.wazesafespace

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.functions.FirebaseFunctions

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private var mGoogleMap: GoogleMap? = null
    private lateinit var shelters: List<Shelter> // Declare shelters list
    private lateinit var textViewMessage: TextView
    private lateinit var database: DatabaseReference
    private val TAG = "MainActivity"
    private lateinit var mFunctions: FirebaseFunctions // Firebase Functions instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize textViewMessage
        textViewMessage = findViewById(R.id.textViewMessage)

        // Initialize Firebase Database
        database = FirebaseDatabase.getInstance().reference
        val myRef = database.child("message")

        // Write a message to the database
        myRef.setValue("Hello, ARI!")

        // Read from the database
        myRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                // This method is called once with the initial value and again
                // whenever data at this location is updated.
                val value = dataSnapshot.getValue(String::class.java)
                Log.d(TAG, "Value is: $value")
                textViewMessage.text = value
            }

            override fun onCancelled(error: DatabaseError) {
                // Failed to read value
                Log.w(TAG, "Failed to read value.", error.toException())
            }
        })

        // Initialize Firebase Functions
        mFunctions = FirebaseFunctions.getInstance()

        // Call the Cloud Function
        callCloudFunction()

        // Load shelters from assets
        shelters = ShelterUtils.loadSheltersFromAssets(this, "shelters.json")

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun callCloudFunction() {
        mFunctions
            .getHttpsCallable("helloWorld") // The name of your Cloud Function
            .call()
            .addOnSuccessListener { result ->
                val response = result.data.toString()
                Log.d(TAG, "Cloud Function Response: $response")
                // Ensure textViewMessage is initialized
                textViewMessage.text = response
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error calling Cloud Function", e)
            }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mGoogleMap = googleMap

        // Add markers for each shelter
        shelters.forEach { shelter ->
            val location = LatLng(shelter.lat, shelter.lon)
            mGoogleMap?.addMarker(MarkerOptions().position(location).title(shelter.name))
        }

        // Optionally, move camera to a specific location (e.g., first shelter)
        shelters.firstOrNull()?.let { firstShelter ->
            val firstLocation = LatLng(firstShelter.lat, firstShelter.lon)
            mGoogleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(firstLocation, 18f))
        }
    }
}
