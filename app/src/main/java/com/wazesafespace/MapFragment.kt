package com.wazesafespace

import android.Manifest
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Html
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
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
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.functions.FirebaseFunctions
import com.google.maps.android.PolyUtil


import org.json.JSONObject
import java.util.Locale


sealed class ShelterEvents {
    data object ShelterFromNotification : ShelterEvents()
    data class ShelterManually(
        val currentLocation: Boolean,
        val address: String? = ""
    ) : ShelterEvents()
}

class MapFragment : Fragment(), OnMapReadyCallback, TextToSpeech.OnInitListener{
    private var mGoogleMap: GoogleMap? = null
    private lateinit var shelters: List<Shelter>
    private val TAG = "MapFragment"
    private lateinit var mFunctions: FirebaseFunctions
    private lateinit var textViewTravelTime: TextView
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private val FINE_PERMISSION_CODE = 1
    private var isMapReady = false
    private var _theView: View? = null
    private val theView: View get() = _theView!!

    // משתנים בשביל הנחיה קולית
    private lateinit var textToSpeech: TextToSpeech
    private var isTtsInitialized = false
    private var isMuted = true
    private lateinit var btnVoiceInstructions: ImageButton

    fun insertAll(shelters: List<Shelter>) {
        FirebaseDatabase.getInstance()
            .getReference("shelters")
            .setValue(shelters)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _theView =  layoutInflater.inflate(R.layout.map_fragment, container,false)
        textToSpeech = TextToSpeech(requireContext(), this)
        return theView
    }


    override fun onDestroy() {
        // סגירת TextToSpeech
        if (textToSpeech != null) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }

        super.onDestroy()
        _theView = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mFunctions = FirebaseFunctions.getInstance()

        shelters = ShelterUtils.loadSheltersFromAssets(requireContext(), "shelters.json")
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        textViewTravelTime = view.findViewById(R.id.textViewTravelTime)

        btnVoiceInstructions = view.findViewById(R.id.btnVoiceInstructions)

        val mapFragment = childFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this) // initialize map after getting location
    }
    fun findShelter(event: ShelterEvents) {

        when (event) {
            is ShelterEvents.ShelterManually -> {
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
            is ShelterEvents.ShelterFromNotification -> {
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
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                FINE_PERMISSION_CODE
            )
            return
        }
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
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
                    Toast.makeText(requireContext(), "Cannot get location.", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(requireContext(), "בשלב זה האפליקציה תומכת רק בעיר באר שבע", Toast.LENGTH_LONG)
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
        shelters.forEach { shelter ->
            val location = LatLng(shelter.lat, shelter.lon)
            mGoogleMap?.addMarker(MarkerOptions().position(location).title(shelter.name))
        }
        moveCameraToCurrentLocation() // קריאה לפונקציה לאחר טעינת המפה

        Handler().postDelayed({ textViewTravelTime.visibility = View.VISIBLE },1000)

    }

    fun onLocation(
        location: Location,
        showDialog: Boolean,
        limitedShieldingTime: Long = 0
    ) {
        val googleMap = mGoogleMap ?: return
        Log.d(TAG, "User location: $location")
        val nearestShelter = findNearestShelter(location, shelters)
        if (nearestShelter != null) {
            val origin = LatLng(location.latitude, location.longitude)
            val dest = LatLng(nearestShelter.lat, nearestShelter.lon)
            //Log.d(TAG, "Requesting directions from $origin to $dest")

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
                        AlertDialog.Builder(requireContext())
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
       /* } else {
            Log.d(TAG, "Current location is null, moving camera to first shelter")
            shelters.firstOrNull()?.let { firstShelter ->
                val firstLocation = LatLng(firstShelter.lat, firstShelter.lon)
                mGoogleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(firstLocation, 18f))
            }
        }*/
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

        val requestQueue = Volley.newRequestQueue(requireContext())
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
        val btnShowInstructions = theView.findViewById<Button>(R.id.btnShowInstructions)
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
                    Toast.makeText(requireContext(), "אין הנחיות לקריאה", Toast.LENGTH_SHORT).show()
                }
            } else {
                btnVoiceInstructions.setImageResource(R.drawable.volume_off)
                isMuted = true
                textToSpeech.stop()  // עצירת ההקראה
            }
        }

        btnShowInstructions.setOnClickListener {
            val dialog = AlertDialog.Builder(requireContext())
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
            for (instruction in instructions) {
                Log.d("TTS", "Speaking out: $instruction")
                textToSpeech.speak(instruction, TextToSpeech.QUEUE_ADD, null, null)
            }

            // מאזין שסיימנו את ההקראה
            textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onDone(utteranceId: String?) {
                    activity?.runOnUiThread {
                        //החלפת אייקון לרמקול מושתק
                        btnVoiceInstructions.setImageResource(R.drawable.volume_off)
                        isMuted = true
                    }
                }

                override fun onError(utteranceId: String?) {
                    Log.e("TTS", "Error in speaking")
                }

                override fun onStart(utteranceId: String?) {
                    Log.d("TTS", "Started speaking utterance $utteranceId")
                }
            })
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