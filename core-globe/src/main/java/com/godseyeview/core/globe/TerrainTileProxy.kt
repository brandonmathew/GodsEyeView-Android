package com.godseyeview.core.globe

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

/** Terrarium terrain proxy (issue #15). The AWS Terrain Tiles S3 bucket
 *  sends no Access-Control-Allow-Origin header, so the WebView page
 *  cannot fetch or decode its tiles directly (CORS blocks both the fetch
 *  and the canvas pixel read the height decode needs). The page instead
 *  requests terrain/{z}/{x}/{y}.png on its own appassets origin; this
 *  proxy fetches the real tile natively - plain HTTP has no CORS - and
 *  returns the bytes as a same-origin image/png response.
 *  shouldInterceptRequest runs on a background thread, so the blocking
 *  fetch is safe here. Returns null for every non-terrain URL. */
object TerrainTileProxy {
    private const val S3_BASE = "https://s3.amazonaws.com/elevation-tiles-prod/terrarium"

    fun fetch(url: Uri): WebResourceResponse? {
        val path = url.path ?: return null
        val marker = "/terrain/"
        val index = path.indexOf(marker)
        if (index < 0) return null
        val parts = path.substring(index + marker.length).split("/")
        if (parts.size != 3) return null
        val z = parts[0].toIntOrNull() ?: return null
        val x = parts[1].toIntOrNull() ?: return null
        val y = parts[2].removeSuffix(".png").toIntOrNull() ?: return null
        if (z !in 0..15 || x < 0 || y < 0) return null
        return try {
            val connection = URL("$S3_BASE/$z/$x/$y.png").openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "GodsEyeViewAndroid")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                return notFound()
            }
            val bytes = connection.inputStream.use { it.readBytes() }
            connection.disconnect()
            WebResourceResponse("image/png", null, ByteArrayInputStream(bytes)).apply {
                setStatusCodeAndReasonPhrase(200, "OK")
            }
        } catch (e: Exception) {
            notFound()
        }
    }

    private fun notFound(): WebResourceResponse =
        WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0))).apply {
            setStatusCodeAndReasonPhrase(404, "Not Found")
        }
}
