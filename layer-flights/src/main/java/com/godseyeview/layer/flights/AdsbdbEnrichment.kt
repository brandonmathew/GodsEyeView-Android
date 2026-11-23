package com.godseyeview.layer.flights

import com.godseyeview.layerapi.CardField
import com.godseyeview.layerapi.Entity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * adsbdb.com enrichment for a selected flight (upstream
 * `server/providers/aircraft/enrichment.js`): callsign → route and
 * hex → aircraft type/registration. Results (including known-missing
 * 404s) are cached in-layer for 24 hours. Every failure returns no
 * fields — enrichment never throws.
 */
internal class AdsbdbEnrichment(
    private val http: HttpClient,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private data class CacheEntry(val atMs: Long, val fields: List<CardField>)

    private val routeCache = ConcurrentHashMap<String, CacheEntry>()
    private val aircraftCache = ConcurrentHashMap<String, CacheEntry>()

    fun enrich(entity: Entity): List<CardField> {
        return try {
            val fields = mutableListOf<CardField>()
            fields += routeFields(entity)
            fields += aircraftFields(entity)
            fields
        } catch (e: Exception) {
            emptyList()
        }
    }

    // -- route ---------------------------------------------------------------

    private fun routeFields(entity: Entity): List<CardField> {
        val callsign = entity.title.trim()
        // No route lookup when the title is just the hex fallback.
        if (callsign.isEmpty() || callsign.equals(entity.id, ignoreCase = true)) {
            return emptyList()
        }
        return cached(routeCache, callsign) { key ->
            val body = try {
                http.get(
                    "https://api.adsbdb.com/v0/callsign/${encode(key)}",
                    connectTimeoutMs = TIMEOUT_MS,
                    readTimeoutMs = TIMEOUT_MS,
                )
            } catch (e: HttpException) {
                if (e.statusCode == 404) return@cached emptyList() // known-missing: cache it
                return@cached null // transient failure: do not cache
            } catch (e: Exception) {
                return@cached null
            }
            parseRoute(body)
        } ?: emptyList()
    }

    private fun parseRoute(body: String): List<CardField>? {
        val flightroute = try {
            val response = Json.parseToJsonElement(body).jsonObject["response"]?.jsonObject
            response?.get("flightroute")?.jsonObject
        } catch (e: Exception) {
            null
        } ?: return emptyList()
        val origin = flightroute["origin"]?.jsonObject
        val destination = flightroute["destination"]?.jsonObject
        if (origin == null || destination == null) return emptyList()

        val fields = mutableListOf<CardField>()
        val originCode = airportCode(origin)
        val destinationCode = airportCode(destination)
        if (originCode != null && destinationCode != null) {
            fields += CardField("Route", "$originCode → $destinationCode")
        }
        airportField(origin)?.let { fields += CardField("Origin", it) }
        airportField(destination)?.let { fields += CardField("Destination", it) }
        text(flightroute["airline"]?.jsonObject, "name")?.let {
            fields += CardField("Airline", it)
        }
        return fields
    }

    // -- aircraft --------------------------------------------------------------

    private fun aircraftFields(entity: Entity): List<CardField> {
        val hex = entity.id.lowercase()
        if (hex.isEmpty()) return emptyList()
        val existingLabels = entity.cardFields.map { it.label }.toSet()
        return cached(aircraftCache, hex) { key ->
            val body = try {
                http.get(
                    "https://api.adsbdb.com/v0/aircraft/${encode(key)}",
                    connectTimeoutMs = TIMEOUT_MS,
                    readTimeoutMs = TIMEOUT_MS,
                )
            } catch (e: HttpException) {
                if (e.statusCode == 404) return@cached emptyList()
                return@cached null
            } catch (e: Exception) {
                return@cached null
            }
            parseAircraft(body, existingLabels)
        } ?: emptyList()
    }

    private fun parseAircraft(body: String, existingLabels: Set<String>): List<CardField>? {
        val aircraft = try {
            val response = Json.parseToJsonElement(body).jsonObject["response"]?.jsonObject
            response?.get("aircraft")?.jsonObject
        } catch (e: Exception) {
            null
        } ?: return emptyList()

        val fields = mutableListOf<CardField>()
        if ("Type" !in existingLabels) {
            val manufacturer = text(aircraft, "manufacturer")
            val type = text(aircraft, "type")
            val typeText = when {
                manufacturer != null && type != null -> "$manufacturer $type"
                type != null -> type
                else -> text(aircraft, "icao_type")
            }
            typeText?.let { fields += CardField("Type", it) }
        }
        if ("Registration" !in existingLabels) {
            text(aircraft, "registration")?.let {
                fields += CardField("Registration", it)
            }
        }
        return fields
    }

    // -- helpers ---------------------------------------------------------------

    /**
     * Cache lookup with a 24 h TTL. The loader returns null for a
     * transient failure (not cached, no fields) or a list (possibly
     * empty for a definitive miss) which is cached.
     */
    private fun cached(
        cache: ConcurrentHashMap<String, CacheEntry>,
        key: String,
        loader: (String) -> List<CardField>?,
    ): List<CardField>? {
        val entry = cache[key]
        if (entry != null && nowMs() - entry.atMs < TTL_MS) return entry.fields
        val loaded = loader(key) ?: return null
        cache[key] = CacheEntry(nowMs(), loaded)
        return loaded
    }

    private fun airportCode(airport: JsonObject): String? =
        text(airport, "iata_code") ?: text(airport, "icao_code")

    /** "code · name" where name is the municipality, else the airport name. */
    private fun airportField(airport: JsonObject): String? {
        val code = airportCode(airport)
        val name = text(airport, "municipality") ?: text(airport, "name")
        return when {
            code != null && name != null -> "$code · $name"
            else -> code ?: name
        }
    }

    private fun text(obj: JsonObject?, key: String): String? {
        if (obj == null) return null
        val prim = obj[key] as? kotlinx.serialization.json.JsonPrimitive ?: return null
        if (!prim.isString) return null
        return prim.content.trim().takeIf { it.isNotEmpty() }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    companion object {
        const val TTL_MS = 24L * 3600L * 1000L
        const val TIMEOUT_MS = 8_000
    }
}
