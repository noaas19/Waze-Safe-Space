package com.wazesafespace

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.jvm.Throws

data class Shelter(
                    val id: String = "",
                    val name: String = "",
                   var reports: Int = 0,
                   val lat: Double = 0.0,
                   val lon: Double = 0.0,
                   var hasStairs: Boolean = false
) {
    suspend fun toSaved(context: Context) = withContext(Dispatchers.IO) {
        val address = ShelterUtils.shelterCoordinatesToAddress(
            context = context,
            lat = lat,
            lon = lon
        )
        SavedShelter(
            name = name,
            lat = lat,
            lon = lon,
            saveDate = System.currentTimeMillis(),
            address = address,
            hasStairs = hasStairs,
            shetlerId = id,
            reportAdded = false
        )
    }
}

data class SavedShelter(
                   var id: String = "",
                   var shetlerId: String = "",
                   var reportAdded: Boolean = false,
                   var name: String = "",
                   var lat: Double = 0.0,
                   var lon: Double = 0.0,
                   var saveDate: Long = System.currentTimeMillis(),
                   var address: String = "",
                   var hasStairs: Boolean = false
)

object ShelterUtils {


    @Throws(Exception::class)
    suspend fun shelterCoordinatesToAddress(
        lat : Double,
        lon: Double,
        context : Context)
    = withContext(Dispatchers.IO) {
        val g = Geocoder(context, Locale.getDefault())

        val results = g.getFromLocation(lat, lon, 1 ) ?:
            throw Exception("Could not find address by coordinates")

        if(results.isNotEmpty()) {
            return@withContext results[0].getAddressLine(0)
        }
        else {
            throw Exception("Could not find address by coordinates")
        }
    }



      fun addShelterReport(savedShelter: SavedShelter,
                           hasStairs: Boolean,
                           callback: (SavedShelter) -> Unit) {
         if(!hasStairs)  return

        FirebaseDatabase.getInstance()
            .getReference("shelters")
            .equalTo("id", savedShelter.shetlerId)
            .get()
            .addOnSuccessListener {  shelterDoc ->
                val shelter = shelterDoc.getValue(Shelter::class.java) ?:return@addOnSuccessListener
                if(shelter.reports >= 1 && !shelter.hasStairs) {
                    shelterDoc.ref.updateChildren(mapOf("hasStairs" to true, "reports" to shelter.reports + 1))
                }

                FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(FirebaseAuth.getInstance().uid!!)
                    .child("shelters")
                    .child(savedShelter.id)
                    .updateChildren(mapOf("reportAdded"  to true))
                    .addOnSuccessListener {
                        savedShelter.reportAdded = true
                        callback(savedShelter)
                        Log.d("Updated", "Updated shetler")
                    }
            }
    }

    suspend fun getMyShelters() = withContext(Dispatchers.IO) {
        val deferred = CompletableDeferred<List<SavedShelter>>()

        FirebaseDatabase.getInstance()
            .getReference("users")
            .child(FirebaseAuth.getInstance().uid!!)
            .child("shelters")
            .get()
            .addOnSuccessListener {
                val shelters = it.children.mapNotNull { shelterDoc -> shelterDoc.getValue(SavedShelter::class.java) }
                deferred.complete(shelters)
            }
            .addOnFailureListener {
                deferred.completeExceptionally(it)
            }
        deferred.await()

    }

    suspend fun saveShelter(context: Context, shelter:Shelter) = withContext(Dispatchers.IO) {
        val deferred = CompletableDeferred<SavedShelter>()
        val savedShelter = shelter.toSaved(context)

        val ref = FirebaseDatabase.getInstance()
            .getReference("users")
            .child(FirebaseAuth.getInstance().uid!!)
            .child("shelters")
            .push()

        savedShelter.id = ref.key!!


        ref.setValue(savedShelter)
            .addOnSuccessListener {
                deferred.complete(savedShelter)
            }
            .addOnFailureListener {
                deferred.completeExceptionally(it)
            }


        deferred.await()
    }


    fun loadSheltersFromAssets(context: Context, fileName: String): List<Shelter> {
        val shelters = mutableListOf<Shelter>()
        try {
            val inputStream = context.assets.open(fileName)
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            val json = String(buffer, StandardCharsets.UTF_8)
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val name = obj.getString("name")
                val lat = obj.getDouble("lat")
                val lon = obj.getDouble("lon")
                shelters.add(Shelter(id="",reports=0,name=name, lat=lat, lon=lon))
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return shelters
    }
}
