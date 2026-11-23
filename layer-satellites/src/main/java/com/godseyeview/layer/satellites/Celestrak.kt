package com.godseyeview.layer.satellites

import java.net.HttpURLConnection
import java.net.URL

/** CelesTrak TLE fetching (Stage 2, issue #27). */
object Celestrak {
    /** Groups fetched for the catalog, in upstream source.js order. */
    val GROUPS: List<String> = listOf(
        "stations",
        "visual",
        "gps-ops",
        "glo-ops",
        "galileo",
        "geo",
        "starlink",
        "resource",
        "weather",
        "planet",
        "sar",
        "science",
    )

    fun fetchGroup(group: String): String {
        val url = URL("https://celestrak.org/NORAD/elements/gp.php?GROUP=$group&FORMAT=tle")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty(
                "User-Agent",
                "GodsEyeView-Android/2.0 (layer-satellites)",
            )
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("CelesTrak $group: HTTP $code")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
