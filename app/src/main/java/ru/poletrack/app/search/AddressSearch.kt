package ru.poletrack.app.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class AddressResult(
    val displayName: String,
    val latitude: Double,
    val longitude: Double
)

object AddressSearch {
    suspend fun search(query: String): List<AddressResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val url = URL(
            "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&accept-language=ru&q=$encoded"
        )

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", "PoleTrack/0.2 Android")
            setRequestProperty("Accept", "application/json")
        }

        try {
            if (connection.responseCode !in 200..299) return@withContext emptyList()
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val array = JSONArray(body)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val lat = item.optString("lat").toDoubleOrNull() ?: continue
                    val lon = item.optString("lon").toDoubleOrNull() ?: continue
                    add(
                        AddressResult(
                            displayName = item.optString("display_name"),
                            latitude = lat,
                            longitude = lon
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection.disconnect()
        }
    }
}
