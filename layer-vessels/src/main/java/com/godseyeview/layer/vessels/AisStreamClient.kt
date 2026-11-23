package com.godseyeview.layer.vessels

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** The AISStream subscription this layer opens with: worldwide bounding
 *  box and the five message types the store merges (upstream defaults in
 *  `server/providers/vessels/ais-live.js`, minus AidsToNavigationReport —
 *  aids are not vessels and this layer does not draw them). Built as a
 *  function so tests can assert the exact shape sent on the wire. */
fun subscriptionJson(apiKey: String): String = buildJsonObject {
    put("APIKey", JsonPrimitive(apiKey))
    put("BoundingBoxes", buildJsonArray {
        add(buildJsonArray {
            add(buildJsonArray { add(JsonPrimitive(-90)); add(JsonPrimitive(-180)) })
            add(buildJsonArray { add(JsonPrimitive(90)); add(JsonPrimitive(180)) })
        })
    })
    put("FilterMessageTypes", buildJsonArray {
        add(JsonPrimitive("PositionReport"))
        add(JsonPrimitive("StandardClassBPositionReport"))
        add(JsonPrimitive("ExtendedClassBPositionReport"))
        add(JsonPrimitive("ShipStaticData"))
        add(JsonPrimitive("StaticDataReport"))
    })
}.toString()

/** Owns the per-phone AISStream WebSocket (plan Q2: no backend — the
 *  phone connects directly with the user's own key). Reconnects with
 *  backoff while started; [stop] cancels everything. */
class AisStreamClient(
    private val store: VesselStore,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val http = OkHttpClient()

    @Volatile
    var connected: Boolean = false
        private set

    /** True once any envelope has arrived on any connection. The layer
     *  uses it to tell "still connecting" apart from "connected, no data
     *  yet worth drawing" — an empty snapshot is a valid live result. */
    @Volatile
    var everReceived: Boolean = false
        private set

    @Volatile private var started = false
    @Volatile private var apiKey: String? = null
    @Volatile private var socket: WebSocket? = null
    private var backoffStep = 0
    private var scheduler: ScheduledExecutorService? = null

    @Synchronized
    fun start(key: String) {
        if (started) return
        started = true
        apiKey = key
        backoffStep = 0
        scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "aisstream-reconnect").apply { isDaemon = true }
        }
        connect()
    }

    @Synchronized
    fun stop() {
        started = false
        apiKey = null
        connected = false
        scheduler?.shutdownNow()
        scheduler = null
        socket?.close(NORMAL_CLOSE, "layer disabled")
        socket = null
    }

    private fun connect() {
        if (!started) return
        val request = Request.Builder().url(STREAM_URL).build()
        socket = http.newWebSocket(request, Listener())
    }

    private fun scheduleReconnect() {
        if (!started) return
        val delaySec = BACKOFF_SECONDS[minOf(backoffStep, BACKOFF_SECONDS.lastIndex)]
        backoffStep++
        val exec = scheduler ?: return
        try {
            exec.schedule({ connect() }, delaySec, TimeUnit.SECONDS)
        } catch (e: Exception) {
            // Scheduler shut down by a racing stop(); nothing to reconnect.
        }
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            connected = true
            backoffStep = 0
            val key = apiKey
            if (key == null) {
                webSocket.close(NORMAL_CLOSE, "stopped")
                return
            }
            webSocket.send(subscriptionJson(key))
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            everReceived = true
            AisMessages.parse(text)?.let { store.apply(it, nowMs()) }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            onMessage(webSocket, bytes.utf8())
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            connected = false
            // Our own close in stop() also lands here; started is false then.
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            connected = false
            scheduleReconnect()
        }
    }

    companion object {
        const val STREAM_URL = "wss://stream.aisstream.io/v0/stream"
        private const val NORMAL_CLOSE = 1000

        /** 5s, 10s, 20s, 40s, then capped at 60s. */
        private val BACKOFF_SECONDS = longArrayOf(5, 10, 20, 40, 60)
    }
}
