package com.wazesafespace

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.OnTokenCanceledListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.PolyUtil
import org.json.JSONObject
import java.util.Locale

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private var mGoogleMap: GoogleMap? = null
    private lateinit var shelters: List<Shelter>
    private lateinit var textViewMessage: TextView
    private lateinit var database: DatabaseReference
    private val TAG = "MainActivity"
    private lateinit var mFunctions: FirebaseFunctions
    private lateinit var textViewTravelTime: TextView
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private val FINE_PERMISSION_CODE = 1
    private var isMapReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textViewMessage = findViewById(R.id.textViewMessage)
        database = FirebaseDatabase.getInstance().reference
        val myRef = database.child("message")
        myRef.setValue("Hello, NOMIMA!")

        myRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val value = dataSnapshot.getValue(String::class.java)
                Log.d(TAG, "Value is: $value")
                textViewMessage.text = value
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Failed to read value.", error.toException())
            }
        })

        mFunctions = FirebaseFunctions.getInstance()
        callCloudFunction()

        shelters = ShelterUtils.loadSheltersFromAssets(this, "shelters.json")
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        getLastLocation()
        textViewTravelTime = findViewById(R.id.textViewTravelTime)


        // Reference to the alerts node
        val alertsRef = database.child("alerts")

        // Listen for real-time updates
        alertsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val alertData = snapshot.value as? Map<String, Any>
                val alertsList = alertData?.get("alerts") as? List<Map<String, Any>>

                if (alertsList != null) {
                    val beerShevaAlert = alertsList.any { alert ->
                        val cities = alert["cities"] as? String
                        cities?.contains("באר שבע") == true
                    }

                    if (beerShevaAlert) {
                        Log.d(TAG, "Alert for Beer Sheva found")

                        // בדיקת מיקום המשתמש
                        if (isUserInBeerSheva(currentLocation)) {
                            Log.d(TAG, "User is in Beer Sheva, showing route,$currentLocation")
                            val nearestShelter = findNearestShelter(currentLocation!!, shelters)
                            if (nearestShelter != null) {
                                val origin = LatLng(currentLocation!!.latitude, currentLocation!!.longitude)
                                val dest = LatLng(nearestShelter.lat, nearestShelter.lon)
                                requestDirections(origin, dest) { response ->
                                    drawRouteOnMap(response, mGoogleMap!!)
                                }
                            }
                        } else {
                            Log.d(TAG, "User is not in Beer Sheva, no route will be shown.")
                        }
                    } else {
                        Log.d(TAG, "No alert for Beer Sheva found, no route will be shown.")
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error fetching data: ", error.toException())
            }
        })
    }


    /**
     * Calls the Firebase Cloud Function and updates the textViewMessage with the response.
     */
    private fun callCloudFunction() {
        mFunctions
            .getHttpsCallable("helloWorld")
            .call()
            .addOnSuccessListener { result ->
                val response = result.data.toString()
                Log.d(TAG, "Cloud Function Response: $response")
                textViewMessage.text = response
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error calling Cloud Function", e)
            }
    }

    /**
     * Gets the last known location of the device.
     * If location permissions are not granted, requests them.
     */
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
                    Log.d(TAG, "latitude & longitude is... $lat $lon")
                    Log.d(TAG, "location is... $location")
                    currentLocation = location // update currentLocation
                    val mapFragment = supportFragmentManager
                        .findFragmentById(R.id.mapFragment) as SupportMapFragment
                    mapFragment.getMapAsync(this) // initialize map after getting location

                    if (isMapReady) {
                        moveCameraToCurrentLocation()
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error getting location", e)
            }
    }

    /**
     * Moves the camera to the current location of the device.
     */
    private fun moveCameraToCurrentLocation() {
        currentLocation?.let {
            val currentLatLng = LatLng(it.latitude, it.longitude)
            Log.d(TAG, "Moving camera to current location: $currentLatLng")
            mGoogleMap?.addMarker(MarkerOptions()
                .position(currentLatLng)
                .title("My Location")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)) // Mark in yellow
            )
            mGoogleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 18f))
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mGoogleMap = googleMap
        isMapReady = true // סימון שהמפה מוכנה
        Log.d(TAG, "onMapReady is called")
        shelters.forEach { shelter ->
            val location = LatLng(shelter.lat, shelter.lon)
            mGoogleMap?.addMarker(MarkerOptions().position(location).title(shelter.name))
        }

        moveCameraToCurrentLocation() // קריאה לפונקציה לאחר טעינת המפה

        if (currentLocation != null) {
            val userLocation = currentLocation!!
            Log.d(TAG, "User location: $userLocation")
            val nearestShelter = findNearestShelter(userLocation, shelters)
            if (nearestShelter != null) {
                val origin = LatLng(userLocation.latitude, userLocation.longitude)
                val dest = LatLng(nearestShelter.lat, nearestShelter.lon)
                Log.d(TAG, "Requesting directions from $origin to $dest")

                // הוספת סמן במיקום המוצא בצבע צהוב
                googleMap.addMarker(MarkerOptions()
                    .position(origin)
                    .title("My Location")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)))

                // הוספת סמן ביעד (המקלט) בצבע כחול
                googleMap.addMarker(MarkerOptions()
                    .position(dest)
                    .title(nearestShelter.name)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)))

                requestDirections(origin, dest) { response ->
                    drawRouteOnMap(response, googleMap)
                }
            }
        } else {
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

    /**
     * Calculates the distance between two locations.
     *
     * @param startLat Starting latitude
     * @param startLng Starting longitude
     * @param endLat Ending latitude
     * @param endLng Ending longitude
     * @return Distance in meters
     */
    private fun calculateDistance(startLat: Double, startLng: Double, endLat: Double, endLng: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(startLat, startLng, endLat, endLng, results)
        return results[0]
    }

    /**
     * Finds the nearest shelter to the user's location.
     *
     * @param userLocation The user's current location
     * @param shelters List of available shelters
     * @return The nearest shelter
     */
    private fun findNearestShelter(userLocation: Location, shelters: List<Shelter>): Shelter? {
        Log.d(TAG, "Finding nearest shelter to user location: $userLocation")
        var nearestShelter: Shelter? = null
        var minDistance = Float.MAX_VALUE

        for (shelter in shelters) {
            val distance = calculateDistance(
                userLocation.latitude,
                userLocation.longitude,
                shelter.lat,
                shelter.lon
            )
            //Log.d(TAG, "Distance to shelter ${shelter.name}: $distance")
            if (distance < minDistance) {
                minDistance = distance
                nearestShelter = shelter
            }
        }

        Log.d(TAG, "Nearest shelter: $nearestShelter")
        return nearestShelter
    }

    /**
     * Creates a URL for the Google Directions API request.
     *
     * @param origin The starting location
     * @param dest The destination location
     * @return The URL for the API request
     */
    private fun getDirectionsUrl(origin: LatLng, dest: LatLng): String {
        val strOrigin = "origin=${origin.latitude},${origin.longitude}"
        val strDest = "destination=${dest.latitude},${dest.longitude}"
        val mode = "mode=walking" // תוכל לשנות ל-driving, walking, bicycling או transit
        val key = "AIzaSyBbd4b2PmNe-yjdGRUCD9crOw5mqlivOqo"
        return "https://maps.googleapis.com/maps/api/directions/json?$strOrigin&$strDest&$mode&key=$key"
    }


    /**
     * Sends a request to the Google Directions API.
     *
     * @param origin The starting location
     * @param dest The destination location
     * @param callback The callback to handle the API response
     */
    private fun requestDirections(origin: LatLng, dest: LatLng, callback: (String) -> Unit) {
        val url = getDirectionsUrl(origin, dest)
        Log.d(TAG, "Requesting directions URL: $url")
        val request = StringRequest(Request.Method.GET, url, Response.Listener { response ->
            Log.d(TAG, "Directions response: $response")
            callback(response)
        }, Response.ErrorListener { error ->
            Log.e(TAG, "Volley Error: ${error.toString()}")
            Log.e(TAG, "Volley Error Network Response: ${error.networkResponse?.statusCode}")
            error.printStackTrace()
        })

        val requestQueue = Volley.newRequestQueue(this)
        requestQueue.add(request)
    }

    /**
     * Draws the route on the map based on the Directions API response.
     *
     * @param response The response from the Directions API
     * @param googleMap The GoogleMap object to draw the route on
     */
    private fun drawRouteOnMap(response: String, googleMap: GoogleMap) {
        Log.d(TAG, "Drawing route on map")
        val jsonObject = JSONObject(response)
        val routes = jsonObject.getJSONArray("routes")
        val points = ArrayList<LatLng>()
        val polylineOptions = PolylineOptions()

        val legs = routes.getJSONObject(0).getJSONArray("legs")
        val steps = legs.getJSONObject(0).getJSONArray("steps")

        // Extract the travel time from the JSON response
        val travelTimeText = legs.getJSONObject(0).getJSONObject("duration").getString("text")
        val travelTimeInSeconds = parseDurationToSeconds(travelTimeText)

        // בדיקה אם זמן המסלול גדול מדקה
        if (travelTimeInSeconds > 60) {
            // Update the TextView with the travel time
            textViewTravelTime.text = "Estimated Travel Time: $travelTimeText"
            // הצגת הודעה למשתמש במידה ואין מסלול בזמן הנדרש
            AlertDialog.Builder(this@MainActivity)
                .setTitle("אין מסלול בטוח בזמן ההתמגנות")
                .setMessage("מומלץ להיכנס לבניין סמוך. אם אין בניין בסביבה, שכב על הרצפה עם ידיים על הראש.")
                .setPositiveButton("אישור") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        } else {
            // Update the TextView with the travel time
            textViewTravelTime.text = "Estimated Travel Time: $travelTimeText"

            // Drawing the route on the map
            for (i in 0 until steps.length()) {
                val step = steps.getJSONObject(i)
                val polyline = step.getJSONObject("polyline")
                val pointsArray = polyline.getString("points")
                points.addAll(PolyUtil.decode(pointsArray))
            }

            polylineOptions.addAll(points)
            polylineOptions.width(10f)
            polylineOptions.color(Color.BLUE)

            googleMap.addPolyline(polylineOptions)
            Log.d(TAG, "Route drawn on map")
        }
    }

    // פונקציה להמרת זמן טקסט לשניות
    private fun parseDurationToSeconds(duration: String): Int {
        var totalSeconds = 0

        if (duration.contains("hour")) {
            val hours = duration.substringBefore(" hour").toInt()
            totalSeconds += hours * 3600
        }

        if (duration.contains("min")) {
            val minutes = duration.substringBefore(" min").toInt()
            totalSeconds += minutes * 60
        }

        if (duration.contains("sec")) {
            val seconds = duration.substringBefore(" sec").toInt()
            totalSeconds += seconds
        }

        return totalSeconds
    }


}


private fun isUserInBeerSheva(location: Location?): Boolean {
    if (location == null) return false
    val lat = location.latitude
    val lon = location.longitude

    // הגדרת תחום קואורדינטות של באר שבע
    val BEER_SHEVA_LAT_MIN = 31.1786
    val BEER_SHEVA_LAT_MAX = 31.3004
    val BEER_SHEVA_LON_MIN = 34.7396
    val BEER_SHEVA_LON_MAX = 34.8416

    return lat >= BEER_SHEVA_LAT_MIN && lat <= BEER_SHEVA_LAT_MAX &&
            lon >= BEER_SHEVA_LON_MIN && lon <= BEER_SHEVA_LON_MAX
}