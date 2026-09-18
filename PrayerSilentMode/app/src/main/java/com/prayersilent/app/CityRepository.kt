package com.prayersilent.app

import android.content.Context
import org.json.JSONObject

/**
 * Reads app/src/main/assets/cities.json.
 * To add more cities/countries, just extend that JSON file - no code changes needed.
 * Format:
 * {
 *   "Country Name": [
 *     {"city": "City Name", "lat": 12.34, "lng": 56.78, "tz": "Region/City"}
 *   ]
 * }
 * "tz" must be a valid IANA time zone id (e.g. "Asia/Karachi", "Europe/London").
 */
object CityRepository {

    fun loadCities(context: Context): Map<String, List<CityInfo>> {
        val json = context.assets.open("cities.json").bufferedReader().use { it.readText() }
        val obj = JSONObject(json)
        val result = LinkedHashMap<String, List<CityInfo>>()

        val countryNames = obj.keys()
        while (countryNames.hasNext()) {
            val country = countryNames.next()
            val arr = obj.getJSONArray(country)
            val cities = mutableListOf<CityInfo>()
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                cities.add(
                    CityInfo(
                        country = country,
                        city = c.getString("city"),
                        latitude = c.getDouble("lat"),
                        longitude = c.getDouble("lng"),
                        timezoneId = c.getString("tz")
                    )
                )
            }
            result[country] = cities
        }
        return result
    }
}
