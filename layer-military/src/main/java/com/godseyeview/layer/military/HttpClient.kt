package com.godseyeview.layer.military

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal blocking HTTP GET helper for the military layer
 * (HttpURLConnection; layers never depend on another layer's module,
 * so this mirrors the flights layer's helper rather than sharing it).
 */
class HttpClient {

    fun get(
        url: String,
        connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
    ): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code for $url")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val USER_AGENT = "GodsEyeView-Android/2.0 (layer-military)"
        const val DEFAULT_CONNECT_TIMEOUT_MS = 10_000
        const val DEFAULT_READ_TIMEOUT_MS = 20_000
    }
}
