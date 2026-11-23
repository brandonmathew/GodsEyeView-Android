package com.godseyeview.layer.flights

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** HTTP failure carrying the upstream status code. */
class HttpException(val statusCode: Int, url: String) :
    IOException("HTTP $statusCode for $url")

/**
 * Minimal blocking HTTP GET helper for the flights layer
 * (HttpURLConnection; no extra dependencies in the layer module).
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
            if (code !in 200..299) throw HttpException(code, url)
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val USER_AGENT = "GodsEyeView-Android/2.0 (layer-flights)"
        const val DEFAULT_CONNECT_TIMEOUT_MS = 10_000
        const val DEFAULT_READ_TIMEOUT_MS = 20_000
    }
}
