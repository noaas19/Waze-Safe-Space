package com.wazesafespace

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import java.io.IOException
import java.nio.charset.StandardCharsets

data class Shelter(val name: String = "",
                   val lat: Double = 0.0,
                   val lon: Double = 0.0,
                   var hasStairs: Boolean = false
)

object ShelterUtils {
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
                shelters.add(Shelter(name, lat, lon))
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return shelters
    }
}
