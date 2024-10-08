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

class MapFragment : AppCompatActivity(), OnMapReadyCallback, TextToSpeech.OnInitListener{
    private var mGoogleMap: GoogleMap? = null
    private lateinit var shelters: List<Shelter>
    private val TAG = "MapFragment"
    private lateinit var mFunctions: FirebaseFunctions
    private lateinit var textViewTravelTime: TextView
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private val FINE_PERMISSION_CODE = 1
    private var isMapReady = false

    // משתנים בשביל הנחיה קולית
    private lateinit var textToSpeech: TextToSpeech
    private var isTtsInitialized = false
    private var isMuted = true
    private lateinit var btnVoiceInstructions: ImageButton



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


    override fun onDestroy() {
        // סגירת TextToSpeech
        if (textToSpeech != null) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }

        super.onDestroy()
    }

    fun findShelter(event: ShelterEvent) {

        when (event.type) {
             "ShelterManually" -> {
                if(event.currentLocation) {
                    getLocation { location ->
                        onLocation(
                            location= location,
                            showDialog = false
                        )
                    }
                }
                else {
                    // get the coordinates for event.address

                }
            }
            "ShelterNotification" -> {
              getLocation {  location ->
                    onLocation(
                        location=location,
                        showDialog = true,
                        limitedShieldingTime = 60
                    )
                }
            }
        }
    }



    /**
     * Gets the location of the device.
     * If location permissions are not granted, requests them.
     */
    fun getLocation(callback: (Location) -> Unit  = {}) {
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
                    Log.d(TAG, "latitude & longitude is... $lat $lon")
                    Log.d(TAG, "location is... $location")
                    currentLocation = location // update currentLocation
                    if (isMapReady) {
                        mGoogleMap?.isMyLocationEnabled = true
                        moveCameraToCurrentLocation()
                    }

                    if(!isUserInBeerSheva(currentLocation))  {
                        Log.d(TAG, "User is not in Beer Sheva, no route will be shown.")
                        Toast.makeText(this, "בשלב זה האפליקציה תומכת רק בעיר באר שבע", Toast.LENGTH_LONG)
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
        if(currentLocation == null) {
            val currentLatLng = LatLng(31.2515, 34.7995)
            mGoogleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 18f))
            return
        }
        currentLocation?.let {
            val currentLatLng = LatLng(it.latitude, it.longitude)
            Log.d(TAG, "Moving camera to current location: $currentLatLng")
//            mGoogleMap?.addMarker(
//                MarkerOptions()
//                    .position(currentLatLng)
//                    .title("User Location")
//                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))
//            )
            mGoogleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 18f))
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mGoogleMap = googleMap
        isMapReady = true // סימון שהמפה מוכנה
        Log.d(TAG, "onMapReady is called")

        moveCameraToCurrentLocation() // קריאה לפונקציה לאחר טעינת המפה

        Handler().postDelayed({ textViewTravelTime.visibility = View.VISIBLE },1000)

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
                val sheltersFromDb = it.children.map { it.getValue(Shelter::class.java)}.filterNotNull()
                shelters = sheltersFromDb
                Log.d("Shetlers", sheltersFromDb.size.toString())
                findShelter(event)
            }
    }



    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when(item.itemId) {
            R.id.menuBack -> {
                finish()
            }
        }

        return super.onOptionsItemSelected(item)
    }

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
                Log.d("Nearst location", "XXx");
                // הוספת סמן במיקום המוצא בצבע צהוב
                googleMap.addMarker(
                    MarkerOptions()
                        .position(origin)
                        .title("My Location")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))
                )

                // הוספת סמן ביעד (המקלט) בצבע כחול
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
                            // הצגת הודעה למשתמש במידה ואין מסלול בזמן הנדרש
                            AlertDialog.Builder(this)
                                .setTitle("אין מסלול בטוח בזמן ההתמגנות")
                                .setMessage("מומלץ להיכנס לבניין סמוך. אם אין בניין בסביבה, שכב על הרצפה עם ידיים על הראש.")
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

    private fun getBestOption(
        currentLocation:Location,
        nearestShelters: List<Shelter>,
        needsAccess: Boolean,
        callback: (Shelter) -> Unit) {
        if(!needsAccess) {
           callback.invoke(nearestShelters[0])
        }
        else {
            if(nearestShelters.size > 1) {
                if(!nearestShelters[0].hasStairs) {
                    callback.invoke(nearestShelters[0])
                }
                else {
                    val theUnAccessible = if(nearestShelters[0].hasStairs) {
                        nearestShelters[0]
                    }
                    else {
                        nearestShelters[1]
                    }
                    val theAccessible = if(theUnAccessible == nearestShelters[0] ) {
                        nearestShelters[1]
                    } else {
                        nearestShelters[0]
                    }

                    requestRouteLength(
                        origin=LatLng(currentLocation.latitude, currentLocation.longitude),
                        dest=LatLng(theUnAccessible.lat, theUnAccessible.lon)
                    ) { distanceFromInaccessible ->

                        requestRouteLength(
                            origin=LatLng(currentLocation.latitude, currentLocation.longitude),
                            dest=LatLng(theAccessible.lat, theAccessible.lon)
                        ) { distanceFromAccessible ->
                            val distanceFromInaccessibleWeighted  = distanceFromInaccessible *  1.5
                            if(distanceFromInaccessibleWeighted < distanceFromAccessible) {
                                callback.invoke(theUnAccessible)
                            }
                            else {
                                callback.invoke(theAccessible)
                            }
                        }
                    }

                }
            }
            else {
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



    // returns 2 shelters at most (based on accessibility)
    // sorted by distance
    private fun filterForRelevantShelter(
        userLocation: Location,
        withAccessibility : Boolean
    ) : List<Shelter> {

        val sorted = shelters.sortedBy { shelter ->
            calculateDistance(
                userLocation.latitude,
                userLocation.longitude,
                shelter.lat,
                shelter.lon
            )
        }
        if(!withAccessibility) {
            return listOf(sorted[0]) // min distance shelter
        }
        // find accessible 1
        val accessible = sorted.firstOrNull { it.hasStairs }
        val nonAccessible = sorted.first { !it.hasStairs }
        if(accessible!= null) {
            return listOf(accessible, nonAccessible)
        }
        return listOf(nonAccessible)
    }
    /**
     * Finds the nearest shelter to the user's location.
     *
     * @param userLocation The user's current location
     * @param shelters List of available shelters
     * @return The nearest shelter
     */
    private fun findNearestShelter(userLocation: Location,
                                   relevantShelters: (List<Shelter>? , needsAccess: Boolean) -> Unit) {
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
        val language = "language=he" // הוספת פרמטר לשפה העברית
        val key = "AIzaSyBbd4b2PmNe-yjdGRUCD9crOw5mqlivOqo"
        return "https://maps.googleapis.com/maps/api/directions/json?$strOrigin&$strDest&$mode&$language&key=$key"
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
        Log.d(TAG, "Requesting directions URL: $url")//מדפיס את כל ההנחיות,אבל זה לא מסודר לעין
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


    private fun requestRouteLength(origin: LatLng, dest: LatLng, callback: (Int) -> Unit) {
        val url = getDirectionsUrl(origin, dest)
        Log.d(TAG, "Requesting directions URL: $url")//מדפיס את כל ההנחיות,אבל זה לא מסודר לעין
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
     * Draws the route on the map based on the Directions API response.
     *
     * @param response The response from the Directions API
     * @param googleMap The GoogleMap object to draw the route on
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
        textViewTravelTime.text = "Estimated Travel Time: $travelTimeText"

        // אתחול הכפתור להצגת ההנחיות
        val btnShowInstructions = findViewById<Button>(R.id.btnShowInstructions)
        val instructionsList = StringBuilder()  // יצירת מחרוזת להצגת כל ההנחיות
        val instructionsForSpeech = mutableListOf<String>()

        // Drawing the route on the map
        for (i in 0 until steps.length()) {
            val step = steps.getJSONObject(i)
            val polyline = step.getJSONObject("polyline")
            val pointsArray = polyline.getString("points")
            points.addAll(PolyUtil.decode(pointsArray))

            // איסוף ההנחיות מכל שלב
            val instructions = step.getString("html_instructions")
            val cleanedInstructions = Html.fromHtml(instructions).toString() // הסרת HTML
            instructionsList.append("שלב ${i + 1}: ").append(cleanedInstructions).append("\n\n")
            instructionsForSpeech.add(cleanedInstructions)
        }

        // הוספת המסלול למפה
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
                textToSpeech.stop()  // עצירת ההקראה
            }
        }

        btnShowInstructions.setOnClickListener {
            val dialog = AlertDialog.Builder(this)
                .setTitle("הנחיות מסלול")
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
                params?.y = 50 // מרחק מהקצה התחתון
                params?.x = 50 // מרחק מהקצה הימני
                window?.attributes = params
            }
            dialog.show()  // הצגת הדיאלוג
        }
        Log.d(TAG, "Route drawn on map")
        return travelTimeInSeconds
    }

    private fun speakOutAllInstructions(instructions: List<String>) {
        if (isTtsInitialized) {
            var instructionsLeft = instructions.size // סופר כמה הנחיות נשארו לקריאה

            textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onDone(utteranceId: String?) {
                    runOnUiThread {
                        instructionsLeft-- // מפחית אחת כל פעם שקטע מוקרא
                        if (instructionsLeft == 0) {
                            // כל ההנחיות הוקראו, מחליף אייקון לרמקול מושתק
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

    // הגדרת תחום קואורדינטות של באר שבע מויקיפדיה
    val BEER_SHEVA_LAT_MIN = 31.174294
    val BEER_SHEVA_LAT_MAX = 31.332650
    val BEER_SHEVA_LON_MIN = 34.706400
    val BEER_SHEVA_LON_MAX = 34.869200
    return lat >= BEER_SHEVA_LAT_MIN && lat <= BEER_SHEVA_LAT_MAX &&
            lon >= BEER_SHEVA_LON_MIN && lon <= BEER_SHEVA_LON_MAX
}