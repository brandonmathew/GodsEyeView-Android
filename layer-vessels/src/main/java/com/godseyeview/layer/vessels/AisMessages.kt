package com.godseyeview.layer.vessels

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/** One parsed AISStream message, reduced to what the vessel store merges.
 *  Produced by [AisMessages.parse]; unknown or garbled envelopes parse to
 *  null, never an exception. */
sealed interface AisUpdate {
    val mmsi: Long

    /** A position fix. Motion fields use the AIS "not available" sentinels
     *  already mapped to null (Sog 102.3, Cog 360, TrueHeading 511). */
    data class Position(
        override val mmsi: Long,
        val lat: Double,
        val lon: Double,
        val sogKn: Double?,
        val cogDeg: Double?,
        val headingDeg: Int?,
        val navStatus: Int?,
        val name: String? = null,
    ) : AisUpdate

    /** Static / voyage data. Every field is optional: AIS static reports
     *  arrive in parts, and the store keeps whatever it already knows. */
    data class Static(
        override val mmsi: Long,
        val name: String?,
        val callsign: String?,
        val imo: Long?,
        val shipType: Int?,
        val destination: String?,
    ) : AisUpdate
}

/** Parser for one AISStream envelope:
 *  `{"MessageType": "...", "MetaData": {"MMSI": ..., "ShipName": ...,
 *  "latitude": ..., "longitude": ...}, "Message": {"<Type>": {...}}}`.
 *  Field names follow the AISStream payloads upstream consumes in
 *  `server/providers/vessels/ais-live.js` / `ais-store.js`. */
object AisMessages {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): AisUpdate? {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            return null
        }
        val messageType = root.string("MessageType") ?: return null
        val meta = root.obj("MetaData") ?: root.obj("Metadata") ?: JsonObject(emptyMap())
        val body = root.obj("Message")?.obj(messageType) ?: JsonObject(emptyMap())
        val mmsi = meta.long("MMSI")
            ?: body.long("UserID") ?: body.long("UserId") ?: body.long("Mmsi")
            ?: return null
        return when (messageType) {
            "PositionReport",
            "StandardClassBPositionReport",
            "ExtendedClassBPositionReport" -> parsePosition(mmsi, body, meta)
            "ShipStaticData" -> AisUpdate.Static(
                mmsi = mmsi,
                name = cleanName(body.string("Name")) ?: cleanName(meta.string("ShipName")),
                callsign = clean(body.string("CallSign")),
                imo = body.long("ImoNumber") ?: body.long("IMO"),
                shipType = body.int("Type") ?: body.int("ShipType"),
                destination = clean(body.string("Destination")),
            )
            "StaticDataReport" -> parseStaticDataReport(mmsi, body, meta)
            else -> null
        }
    }

    private fun parsePosition(mmsi: Long, body: JsonObject, meta: JsonObject): AisUpdate.Position? {
        // The message body carries the fix; MetaData repeats the last known
        // position and is the fallback when the body lacks one.
        val lat = body.double("Latitude") ?: meta.double("latitude") ?: meta.double("Latitude")
        val lon = body.double("Longitude") ?: meta.double("longitude") ?: meta.double("Longitude")
        if (lat == null || lon == null) return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        val sog = body.double("Sog")?.takeIf { it < SOG_NOT_AVAILABLE }
        val cog = body.double("Cog")?.takeIf { it < 360.0 }
        val heading = body.int("TrueHeading")?.takeIf { it in 0..359 }
        return AisUpdate.Position(
            mmsi = mmsi,
            lat = lat,
            lon = lon,
            sogKn = sog,
            cogDeg = cog,
            headingDeg = heading,
            navStatus = body.int("NavigationalStatus"),
            name = cleanName(body.string("Name")) ?: cleanName(meta.string("ShipName")),
        )
    }

    private fun parseStaticDataReport(mmsi: Long, body: JsonObject, meta: JsonObject): AisUpdate.Static {
        // Class B static data arrives as two parts; a part flagged invalid
        // contributes nothing (upstream ais-store.js does the same).
        val reportA = body.obj("ReportA")?.takeIf { it.boolean("Valid") != false }
        val reportB = body.obj("ReportB")?.takeIf { it.boolean("Valid") != false }
        return AisUpdate.Static(
            mmsi = mmsi,
            name = cleanName(reportA?.string("Name")) ?: cleanName(meta.string("ShipName")),
            callsign = clean(reportA?.string("CallSign")),
            imo = reportA?.long("ImoNumber") ?: reportA?.long("IMO"),
            shipType = reportB?.int("ShipType") ?: reportB?.int("Type"),
            destination = clean(reportB?.string("Destination")),
        )
    }

    /** AIS "not available" sentinel for speed over ground, in knots. */
    private const val SOG_NOT_AVAILABLE = 102.3

    // -- tolerant field accessors: AISStream mixes numbers and strings ----

    private fun JsonObject.prim(key: String): JsonPrimitive? = this[key] as? JsonPrimitive
    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.string(key: String): String? = prim(key)?.contentOrNull
    private fun JsonObject.double(key: String): Double? =
        prim(key)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }
    private fun JsonObject.int(key: String): Int? =
        prim(key)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }
    private fun JsonObject.long(key: String): Long? =
        prim(key)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }
    private fun JsonObject.boolean(key: String): Boolean? = prim(key)?.contentOrNull?.toBooleanStrictOrNull()

    /** Trim padding; blank (or AIS `@` padding) becomes null. */
    private fun clean(value: String?): String? =
        value?.trim()?.trim('@')?.trim()?.takeIf { it.isNotEmpty() }

    /** Ship names arrive padded with spaces and `@` fill characters. */
    private fun cleanName(value: String?): String? = clean(value)
}
