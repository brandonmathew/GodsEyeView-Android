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
fun subscriptionJson(apiKey: String): String =
    subscriptionJson(apiKey, listOf(com.godseyeview.layerapi.LatLonBox.GLOBAL))

/** Subscription for a specific set of bounding boxes (Q25, issue #40):
 *  the viewport filter cap's box — one box normally, two when it wraps
 *  the antimeridian, the global box before the first camera report. */
fun subscriptionJson(
    apiKey: String,
    boxes: List<com.godseyeview.layerapi.LatLonBox>,
): String = buildJsonObject {
    put("APIKey", JsonPrimitive(apiKey))
    put("BoundingBoxes", buildJsonArray {
        for (box in boxes) {
            add(buildJsonArray {
                add(buildJsonArray {
                    add(coord(box.minLat)); add(coord(box.minLon))
                })
                add(buildJsonArray {
                    add(coord(box.maxLat)); add(coord(box.maxLon))
                })
            })
        }
    })
    put("FilterMessageTypes", buildJsonArray {
        add(JsonPrimitive("PositionReport"))
        add(JsonPrimitive("StandardClassBPositionReport"))
        add(JsonPrimitive("ExtendedClassBPositionReport"))
        add(JsonPrimitive("ShipStaticData"))
        add(JsonPrimitive("StaticDataReport"))
    })
}.toString()

/** A box coordinate on the wire: whole degrees stay integers, as the
 *  original worldwide subscription wrote them (-90, not -90.0). */
private fun coord(value: Double): JsonPrimitive =
    if (value == Math.floor(value) && Math.abs(value) < 1e15) {
        JsonPrimitive(value.toLong())
    } else {
        JsonPrimitive(value)
    }

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

    /** The subscription's current bounding boxes (Q25). Starts global;
     *  the layer narrows it to the viewport cap's box on refilters. */
    @Volatile
    var boundingBoxes: List<com.godseyeview.layerapi.LatLonBox> =
        listOf(com.godseyeview.layerapi.LatLonBox.GLOBAL)
        private set

    /** Narrow/widen the subscription area (Q25). Called ONLY on the
     *  core's hysteresis triggers (refilter or poll) — never per camera
     *  move. A changed box set is re-sent on the live socket; if the
     *  socket is down, the next connection's onOpen sends the current
     *  boxes. Unchanged boxes are a no-op. */
    @Synchronized
    fun updateBoundingBoxes(boxes: List<com.godseyeview.layerapi.LatLonBox>) {
        val normalized = boxes.takeIf { it.isNotEmpty() }
            ?: listOf(com.godseyeview.layerapi.LatLonBox.GLOBAL)
        if (normalized == boundingBoxes) return
        boundingBoxes = normalized
        val key = apiKey
        if (started && key != null) {
            socket?.send(subscriptionJson(key, normalized))
        }
    }

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
            webSocket.send(subscriptionJson(key, boundingBoxes))
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
