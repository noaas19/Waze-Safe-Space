package com.wazesafespace

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.OnTokenCanceledListener
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

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private val FINE_PERMISSION_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize textViewMessage
        textViewMessage = findViewById(R.id.textViewMessage)

        // Initialize Firebase Database
        database = FirebaseDatabase.getInstance().reference
        val myRef = database.child("message")

        // Write a message to the database
        myRef.setValue("Hello, NOMIMA!")

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

        // Initialize fusedLocationProviderClient
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        // Get the last location
        getLastLocation()
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

    private fun getLastLocation() {
        Log.d(TAG, "getLastLocation called")
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
            return
        }
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                FINE_PERMISSION_CODE
            )
            return
        }

        Log.d(TAG, "fusedLocationProviderClient called")
        fusedLocationProviderClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            object : CancellationToken() {
                override fun onCanceledRequested(p0: OnTokenCanceledListener) =
                    CancellationTokenSource().token

                override fun isCancellationRequested() = false
            })
            .addOnSuccessListener { location: Location? ->
                if (location == null) {
                    Log.d(TAG, "location is null")
                    Toast.makeText(this, "Cannot get location.", Toast.LENGTH_SHORT).show()
                } else {
                    val lat = location.latitude
                    val lon = location.longitude
                    Log.d(TAG, "latitude &  is... $lat $lon")
                    Log.d(TAG, "location is... $location")
                    mGoogleMap?.addMarker(MarkerOptions().position(LatLng(location.latitude, location.longitude)).title("THE LOCATION"))


                }
            }


//        val locationRequest = CurrentLocationRequest.Builder()
//        locationRequest.setPriority(Priority.PRIORITY_HIGH_ACCURACY)
//        var location = fusedLocationProviderClient.getCurrentLocation(locationRequest.build(),null)
//        Log.d(TAG, "location is.... $location")
//        Log.d(TAG, "location is.... ${location.isSuccessful}")
//        Log.d(TAG, "location is.... ${location.isComplete}")
//        Log.d(TAG, "location is.... ${location.exception}")
//        var temp = location.result.latitude
//
//        Log.d(TAG, "latitude... $temp")
//        fusedLocationProviderClient.lastLocation.addOnSuccessListener { location ->
//            if (location != null) {
//                currentLocation = location
//                Log.d(TAG, "Current location is not null: $currentLocation")
//                val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
//                mapFragment.getMapAsync(this)
//            } else {
//                Log.d(TAG, "Current location is null")
//            }
//        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mGoogleMap = googleMap
        Log.d(TAG,"onMapReady is called")
        // Add markers for each shelter
        shelters.forEach { shelter ->
            val location = LatLng(shelter.lat, shelter.lon)
            mGoogleMap?.addMarker(MarkerOptions().position(location).title(shelter.name))
        }

        // Move camera to current location if available
        currentLocation?.let {
            val currentLatLng = LatLng(it.latitude, it.longitude)
            Log.d(TAG, "Moving camera to current location: $currentLatLng")
            mGoogleMap?.addMarker(MarkerOptions().position(currentLatLng).title("My Location"))
            mGoogleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 18f))
        } ?: run {
            Log.d(TAG, "Current location is null, moving camera to first shelter")
            shelters.firstOrNull()?.let { firstShelter ->
                val firstLocation = LatLng(firstShelter.lat, firstShelter.lon)
                mGoogleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(firstLocation, 18f))
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == FINE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLastLocation()
            } else {
                Toast.makeText(this, "Location permission is denied, please allow the permission", Toast.LENGTH_SHORT).show()
            }
        }
    }
}