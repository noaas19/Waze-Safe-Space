package com.wazesafespace

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Html
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.lifecycleScope
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.gson.Gson
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.launch


import org.json.JSONObject
import java.util.Locale


data class ShelterEvent(
    val type: String = "",
    var currentLocation: Boolean = false,
    var address: String? = null
)

class MapFragment : AppCompatActivity(), OnMapReadyCallback, TextToSpeech.OnInitListener {
    private var mGoogleMap: GoogleMap? = null
    private lateinit var shelters: List<Shelter>
    private val TAG = "MapFragment"
    private lateinit var mFunctions: FirebaseFunctions
    private lateinit var textViewTravelTime: TextView
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private val FINE_PERMISSION_CODE = 1
    private var isMapReady = false

    private lateinit var textToSpeech: TextToSpeech
    private var isTtsInitialized = false
    private var isMuted = true
    private lateinit var btnVoiceInstructions: ImageButton

    /**
     * Handles the map initialization and sets up the UI elements.
     * Also loads the shelters and requests location updates.
     * @param savedInstanceState The saved instance state (if any).
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.map_fragment)
        textToSpeech = TextToSpeech(this, this)

        mFunctions = FirebaseFunctions.getInstance()

        shelters = ShelterUtils.loadSheltersFromAssets(this, "shelters.json")

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        textViewTravelTime = findViewById(R.id.textViewTravelTime)

        btnVoiceInstructions = findViewById(R.id.btnVoiceInstructions)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this) // initialize map after getting location
        val menuIcon = findViewById<ImageView>(R.id.menuBtn)
        menuIcon.setOnClickListener {
            val popUpMenu = PopupMenu(this@MapFragment, it)
            popUpMenu.inflate(R.menu.menu_nav_back)
            popUpMenu.setOnMenuItemClickListener(::onOptionsItemSelected)
            popUpMenu.show()
        }
    }

    /**
     * Cleans up resources when the activity is destroyed, specifically stopping the TextToSpeech engine.
     */
    override fun onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        super.onDestroy()
    }

    /**
     * Handles the event to find the nearest shelter based on the event type.
     * @param event The shelter event containing the type, current location flag, and optional address.
     */
    fun findShelter(event: ShelterEvent) {

        when (event.type) {
            "ShelterManually" -> {
                if (event.currentLocation) {
                    getLocation { location ->
                        onLocation(
                            location = location,
                            showDialog = false
                        )
                    }
                } else {
                    // get the coordinates for event.address
                }
            }

            "ShelterNotification" -> {
                getLocation { location ->
                    onLocation(
                        location = location,
                        showDialog = true,
                        limitedShieldingTime = 60
                    )
                }
            }
        }
    }


    /**
     * Gets the current location of the device using FusedLocationProviderClient.
     * If location permissions are not granted, requests them.
     * @param callback Function to call with the location once obtained.
     */
    fun getLocation(callback: (Location) -> Unit = {}) {
        Log.d(TAG, "getLocation called")
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
                    Log.d(TAG, "latitude & longitude are: $lat $lon")
                    currentLocation = location // update currentLocation
                    if (isMapReady) {
                        mGoogleMap?.isMyLocationEnabled = true
                        moveCameraToCurrentLocation()
                    }

                    if (!isUserInBeerSheva(currentLocation)) {
                        Log.d(TAG, "User is not in Beer Sheva, no route will be shown.")
                        Toast.makeText(
                            this,
                            "בשלב זה האפליקציה תומכת רק בעיר באר שבע",
                            Toast.LENGTH_LONG
                        )
                            .show()
                        return@addOnSuccessListener
                    }
                    Log.d(TAG, "User is in Beer Sheva, showing route,$currentLocation")
                    callback.invoke(location)
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
        if (currentLocation == null) {
            val currentLatLng = LatLng(31.2515, 34.7995)
            mGoogleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 18f))
            return
        }
        currentLocation?.let {
            val currentLatLng = LatLng(it.latitude, it.longitude)
            Log.d(TAG, "Moving camera to current location: $currentLatLng")
            mGoogleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 18f))
        }
    }

    /**
     * Called when the map is ready for use.
     * Loads shelter markers and retrieves shelter data from Firebase.
     * @param googleMap The GoogleMap object ready for use.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mGoogleMap = googleMap
        isMapReady = true
        Log.d(TAG, "onMapReady is called")
        moveCameraToCurrentLocation()
        Handler().postDelayed({ textViewTravelTime.visibility = View.VISIBLE }, 1000)

        val eventString = intent.getStringExtra("event") ?: return
        val gson = Gson()
        val event = gson.fromJson(eventString, ShelterEvent::class.java)

        shelters.forEach { shelter ->
            val location = LatLng(shelter.lat, shelter.lon)
            mGoogleMap?.addMarker(MarkerOptions().position(location).title(shelter.name))
        }

        FirebaseDatabase.getInstance()
            .getReference("shelters")
            .get()
            .addOnSuccessListener {
                val sheltersFromDb =
                    it.children.map { it.getValue(Shelter::class.java) }.filterNotNull()
                shelters = sheltersFromDb
                Log.d("Shetlers", sheltersFromDb.size.toString())
                findShelter(event)
            }
    }

    /**
     * Handles the selection of options from the menu, such as navigating back.
     * @param item The selected menu item.
     * @return True if the item was handled, false otherwise.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {
            R.id.menuBack -> {
                finish()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Handles the user's location and finds the nearest shelter.
     * Optionally displays a dialog if the route exceeds the limited shielding time.
     * @param location The user's current location.
     * @param showDialog Whether to show the dialog.
     * @param limitedShieldingTime Time limit for safe routes (in seconds).
     */
    fun onLocation(
        location: Location,
        showDialog: Boolean,
        limitedShieldingTime: Long = 0
    ) {
        val googleMap = mGoogleMap ?: return
        Log.d(TAG, "User location: $location")
        findNearestShelter(location) { nearestShelters, needsAccess ->
            val shelters = nearestShelters ?: return@findNearestShelter
            getBestOption(location, shelters, needsAccess) { nearestShelter ->

                lifecycleScope.launch {
                    val saved = ShelterUtils.saveShelter(this@MapFragment, nearestShelter)
                }
                val origin = LatLng(location.latitude, location.longitude)
                val dest = LatLng(nearestShelter.lat, nearestShelter.lon)

                googleMap.addMarker(
                    MarkerOptions()
                        .position(origin)
                        .title("My Location")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))
                )

                googleMap.addMarker(
                    MarkerOptions()
                        .position(dest)
                        .title(nearestShelter.name)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                )

                requestDirections(origin, dest) { response ->

                    drawRouteOnMap(response, googleMap)
                    val travelTimeInSeconds = drawRouteOnMap(response, mGoogleMap!!)
                    if (showDialog && limitedShieldingTime > 0) {
                        if (travelTimeInSeconds > limitedShieldingTime) {

                            AlertDialog.Builder(this)
                                .setTitle("אין מסלול בטוח בזמן ההתמגנות          ")
                                .setMessage("מומלץ להיכנס לבניין סמוך.\n אם אין בניין בסביבה,\nשכב על הרצפה עם ידיים על הראש.")
                                .setPositiveButton("אישור") { dialog, _ ->
                                    dialog.dismiss()
                                }
                                .show()
                        }
                    }
                }
            }
        }
        /* } else {
             Log.d(TAG, "Current location is null, moving camera to first shelter")
             shelters.firstOrNull()?.let { firstShelter ->
                 val firstLocation = LatLng(firstShelter.lat, firstShelter.lon)
                 mGoogleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(firstLocation, 18f))
             }
         }*/
    }

    /**
     * Finds the best shelter option based on accessibility needs and distance.
     * @param currentLocation The user's current location.
     * @param nearestShelters List of the nearest shelters.
     * @param needsAccess Whether accessibility is needed.
     * @param callback Callback to return the selected shelter.
     */
    private fun getBestOption(
        currentLocation: Location,
        nearestShelters: List<Shelter>,
        needsAccess: Boolean,
        callback: (Shelter) -> Unit
    ) {
        if (!needsAccess) {
            callback.invoke(nearestShelters[0])
        } else {
            if (nearestShelters.size > 1) {
                if (!nearestShelters[0].hasStairs) {
                    callback.invoke(nearestShelters[0])
                } else {
                    val theUnAccessible = if (nearestShelters[0].hasStairs) {
                        nearestShelters[0]
                    } else {
                        nearestShelters[1]
                    }
                    val theAccessible = if (theUnAccessible == nearestShelters[0]) {
                        nearestShelters[1]
                    } else {
                        nearestShelters[0]
                    }

                    requestRouteLength(
                        origin = LatLng(currentLocation.latitude, currentLocation.longitude),
                        dest = LatLng(theUnAccessible.lat, theUnAccessible.lon)
                    ) { distanceFromInaccessible ->

                        requestRouteLength(
                            origin = LatLng(currentLocation.latitude, currentLocation.longitude),
                            dest = LatLng(theAccessible.lat, theAccessible.lon)
                        ) { distanceFromAccessible ->
                            val distanceFromInaccessibleWeighted = distanceFromInaccessible * 2
                            if (distanceFromInaccessibleWeighted < distanceFromAccessible) {
                                callback.invoke(theUnAccessible)
                            } else {
                                callback.invoke(theAccessible)
                            }
                        }
                    }
                }
            } else {
                callback.invoke(nearestShelters[0])
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
    private fun calculateDistance(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double
    ): Float {
        val results = FloatArray(1)
        Location.distanceBetween(startLat, startLng, endLat, endLng, results)
        return results[0]
    }

    /**
     * Filters shelters based on accessibility and sorts them by distance to the user's location.
     * Returns up to two shelters based on the accessibility requirement.
     * @param userLocation The user's current location.
     * @param withAccessibility Whether accessibility is required.
     * @return List of up to two shelters.
     */
    private fun filterForRelevantShelter(
        userLocation: Location,
        withAccessibility: Boolean
    ): List<Shelter> {

        val sorted = shelters.sortedBy { shelter ->
            calculateDistance(
                userLocation.latitude,
                userLocation.longitude,
                shelter.lat,
                shelter.lon
            )
        }
        if (!withAccessibility) {
            return listOf(sorted[0]) // min distance shelter
        }
        // find accessible 1
        val accessible = sorted.firstOrNull { it.hasStairs }
        val nonAccessible = sorted.first { !it.hasStairs }
        if (accessible != null) {
            return listOf(accessible, nonAccessible)
        }
        return listOf(nonAccessible)
    }

    /**
     * Finds the nearest shelter to the user's current location and filters shelters by accessibility.
     * @param userLocation The user's current location.
     * @param relevantShelters Callback to return the list of relevant shelters and accessibility need.
     */
    private fun findNearestShelter(
        userLocation: Location,
        relevantShelters: (List<Shelter>?, needsAccess: Boolean) -> Unit
    ) {
        Log.d(TAG, "Finding nearest shelter to user location: $userLocation")

        val userId = FirebaseAuth.getInstance().uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener {
                val user = it.toObject(User::class.java) ?: return@addOnSuccessListener
                val needsAccess = user.accessibility != "ללא מגבלת נגישות"
                val shelters = filterForRelevantShelter(userLocation, needsAccess)
                Log.d("Sorted shelters", shelters.size.toString())
                relevantShelters.invoke(shelters, needsAccess)
            }
            .addOnFailureListener {
                relevantShelters.invoke(null, false)
                Log.d("Error", it.message.toString())
            }
    }

    /**
     * Constructs the URL for the Google Directions API request.
     * @param origin The starting location.
     * @param dest The destination location.
     * @return The constructed URL.
     */
    private fun getDirectionsUrl(origin: LatLng, dest: LatLng): String {
        val strOrigin = "origin=${origin.latitude},${origin.longitude}"
        val strDest = "destination=${dest.latitude},${dest.longitude}"
        val mode = "mode=walking"
        val language = "language=he"
        val key = "AIzaSyBbd4b2PmNe-yjdGRUCD9crOw5mqlivOqo"
        return "https://maps.googleapis.com/maps/api/directions/json?$strOrigin&$strDest&$mode&$language&key=$key"
    }


    /**
     * Sends a request to the Google Directions API to get the directions from origin to destination.
     * @param origin The starting location.
     * @param dest The destination location.
     * @param callback The callback to handle the API response.
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
     * Requests the length of a route between two locations using the Google Directions API.
     * @param origin The starting location.
     * @param dest The destination location.
     * @param callback The callback to handle the route length.
     */
    private fun requestRouteLength(origin: LatLng, dest: LatLng, callback: (Int) -> Unit) {
        val url = getDirectionsUrl(origin, dest)
        //Log.d(TAG, "Requesting directions URL: $url")
        val request = StringRequest(Request.Method.GET, url, { response ->
            Log.d(TAG, "Directions response: $response")
            val jsonObject = JSONObject(response)
            val routes = jsonObject.getJSONArray("routes")
            val legs = routes.getJSONObject(0).getJSONArray("legs")
            val travelTimeText = legs.getJSONObject(0)
                .getJSONObject("duration")
                .getString("text")
            val dur = parseDurationToSeconds(travelTimeText)
            callback(dur)
        }, { error ->
            Log.e(TAG, "Volley Error: ${error.toString()}")
            Log.e(TAG, "Volley Error Network Response: ${error.networkResponse?.statusCode}")
            error.printStackTrace()
        })

        val requestQueue = Volley.newRequestQueue(this)
        requestQueue.add(request)
    }
    /**
     * Draws the route on the Google Map based on the Directions API response.
     * Also updates the travel time and handles voice instructions and dialog display.
     * @param response The response from the Directions API.
     * @param googleMap The GoogleMap object to draw the route on.
     * @return The travel time in seconds.
     */
    private fun drawRouteOnMap(response: String, googleMap: GoogleMap): Int {
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

        // Update the TextView with the travel time
        textViewTravelTime.text = "$travelTimeText"

        val btnShowInstructions = findViewById<LinearLayout>(R.id.btnShowInstructions)
        val instructionsList = StringBuilder()  // יצירת מחרוזת להצגת כל ההנחיות
        val instructionsForSpeech = mutableListOf<String>()

        // Drawing the route on the map
        for (i in 0 until steps.length()) {
            val step = steps.getJSONObject(i)
            val polyline = step.getJSONObject("polyline")
            val pointsArray = polyline.getString("points")
            points.addAll(PolyUtil.decode(pointsArray))

            val instructions = step.getString("html_instructions")
            val cleanedInstructions = Html.fromHtml(instructions).toString() // הסרת HTML
            instructionsList.append("שלב ${i + 1}: ").append(cleanedInstructions).append("\n\n")
            instructionsForSpeech.add(cleanedInstructions)
        }

        // Adding the route to the map
        polylineOptions.addAll(points)
        polylineOptions.width(10f)
        polylineOptions.color(Color.BLUE)
        googleMap.addPolyline(polylineOptions)

        btnVoiceInstructions.setOnClickListener {
            if (isMuted) {
                btnVoiceInstructions.setImageResource(R.drawable.volume_up)
                isMuted = false
                if (instructionsForSpeech.isNotEmpty()) {
                    speakOutAllInstructions(instructionsForSpeech)
                } else {
                    Toast.makeText(this, "אין הנחיות לקריאה", Toast.LENGTH_SHORT).show()
                }
            } else {
                btnVoiceInstructions.setImageResource(R.drawable.volume_off)
                isMuted = true
                textToSpeech.stop()
            }
        }

        btnShowInstructions.setOnClickListener {
            val dialog = AlertDialog.Builder(this)
                .setTitle("הנחיות מסלול                ")
                .setMessage(instructionsList.toString())
                .setPositiveButton("סגור") { dialog, _ ->
                    dialog.dismiss()
                }
                .create()

            dialog.setOnShowListener {
                val window = dialog.window
                window?.setLayout(
                    (resources.displayMetrics.widthPixels * 0.9).toInt(),
                    (resources.displayMetrics.heightPixels * 0.4).toInt()
                )
                val params = window?.attributes
                params?.gravity = Gravity.BOTTOM or Gravity.END
                params?.y = 50
                params?.x = 50
                window?.attributes = params
            }
            dialog.show()
        }
        Log.d(TAG, "Route drawn on map")
        return travelTimeInSeconds
    }

    /**
     * Uses TextToSpeech to speak out all route instructions.
     * @param instructions The list of instructions to be spoken.
     */
    private fun speakOutAllInstructions(instructions: List<String>) {
        if (isTtsInitialized) {
            var instructionsLeft = instructions.size // סופר כמה הנחיות נשארו לקריאה

            textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onDone(utteranceId: String?) {
                    runOnUiThread {
                        instructionsLeft--
                        if (instructionsLeft == 0) {
                            btnVoiceInstructions.setImageResource(R.drawable.volume_off)
                            isMuted = true
                        }
                    }
                }

                override fun onError(utteranceId: String?) {
                    Log.e("TTS", "Error in speaking")
                }

                override fun onStart(utteranceId: String?) {
                    Log.d("TTS", "Started speaking utterance $utteranceId")
                }
            })

            var utteranceCount = 0
            for (instruction in instructions) {
                val utteranceId = "utterance_$utteranceCount"
                utteranceCount++
                Log.d("TTS", "Speaking out: $instruction with utteranceId: $utteranceId")
                textToSpeech.speak(instruction, TextToSpeech.QUEUE_ADD, null, utteranceId)
            }
        } else {
            Log.e("TTS", "TextToSpeech is not initialized")
        }
    }

    /**
     * Initializes the TextToSpeech engine and sets the language to Hebrew.
     * @param status The initialization status.
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale("he"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Language not supported")
            } else {
                isTtsInitialized = true
            }
        } else {
            Log.e("TTS", "Initialization failed")
        }
    }

    /**
     * Parses a travel time string (e.g., "1 hour 20 min") into seconds.
     * @param duration The duration string to parse.
     * @return The duration in seconds.
     */
    private fun parseDurationToSeconds(duration: String): Int {
        var totalSeconds = 0

        // Regex to find all numbers with their respective time units
        val regex = Regex("(\\d+)\\s*(hour|hours|min|minutes|sec|seconds)")

        regex.findAll(duration).forEach { matchResult ->
            val value = matchResult.groupValues[1].toInt() // Extract the numeric part
            val unit = matchResult.groupValues[2] // Extract the unit part

            when (unit) {
                "hour", "hours" -> totalSeconds += value * 3600
                "min", "minutes" -> totalSeconds += value * 60
                "sec", "seconds" -> totalSeconds += value
            }
        }

        return totalSeconds
    }
}

/**
 * Checks if the user's current location is within the coordinates of Beer Sheva (or Kiryat Gat).
 * @param location The user's current location.
 * @return True if the user is in Beer Sheva (or Kiryat Gat), false otherwise.
 */
private fun isUserInBeerSheva(location: Location?): Boolean {
    if (location == null) return false
    val lat = location.latitude
    val lon = location.longitude

    // Coordinates area of Beer Sheva
    val BEER_SHEVA_LAT_MIN = 31.174294
    val BEER_SHEVA_LAT_MAX = 31.332650
    val BEER_SHEVA_LON_MIN = 34.706400
    val BEER_SHEVA_LON_MAX = 34.869200

    // Kiryat Gat coordinate field for a visualization video
    val KIRYAT_GAT_LAT_MIN = 31.589500
    val KIRYAT_GAT_LAT_MAX = 31.642800
    val KIRYAT_GAT_LON_MIN = 34.738800
    val KIRYAT_GAT_LON_MAX = 34.799400

    //Checking whether the user is in the relevant field
    val isInBeerSheva = lat >= BEER_SHEVA_LAT_MIN && lat <= BEER_SHEVA_LAT_MAX &&
            lon >= BEER_SHEVA_LON_MIN && lon <= BEER_SHEVA_LON_MAX

    val isInKiryatGat = lat >= KIRYAT_GAT_LAT_MIN && lat <= KIRYAT_GAT_LAT_MAX &&
            lon >= KIRYAT_GAT_LON_MIN && lon <= KIRYAT_GAT_LON_MAX

    return isInBeerSheva || isInKiryatGat
}
