package com.godseyeview.layer.vessels

import java.util.concurrent.ConcurrentHashMap

/** Everything the detail card shows for one vessel. Position fields are
 *  null until the first position report for the MMSI arrives. */
data class VesselRecord(
    val mmsi: Long,
    val name: String? = null,
    val callsign: String? = null,
    val imo: Long? = null,
    val shipType: Int? = null,
    val destination: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val sogKn: Double? = null,
    val cogDeg: Double? = null,
    val headingDeg: Int? = null,
    val navStatus: Int? = null,
    /** When the last position report arrived (epoch ms); 0 = never. A
     *  static report does not restamp this (upstream ais-store.js: a static
     *  message's MetaData repeats an old fix, it is not a new one). */
    val lastReportMs: Long = 0L,
    /** When any report for this MMSI last arrived; bounds how long a
     *  position-less record is retained. */
    val lastSeenMs: Long = 0L,
)

/** In-memory vessel cache, merged per MMSI like upstream `ais-store.js`:
 *  static data fills the identity fields, position reports move the vessel.
 *  Thread-safe: the WebSocket thread applies updates while the core's
 *  fetch thread takes snapshots. */
class VesselStore {
    private val records = ConcurrentHashMap<Long, VesselRecord>()

    fun apply(update: AisUpdate, nowMs: Long) {
        records.compute(update.mmsi) { _, existing ->
            val base = existing ?: VesselRecord(mmsi = update.mmsi)
            when (update) {
                is AisUpdate.Position -> base.copy(
                    name = update.name ?: base.name,
                    lat = update.lat,
                    lon = update.lon,
                    sogKn = update.sogKn,
                    cogDeg = update.cogDeg,
                    headingDeg = update.headingDeg,
                    navStatus = update.navStatus,
                    lastReportMs = nowMs,
                    lastSeenMs = nowMs,
                )
                is AisUpdate.Static -> base.copy(
                    // A static report only replaces what it actually carries;
                    // partial Class B parts must not blank known values.
                    name = update.name ?: base.name,
                    callsign = update.callsign ?: base.callsign,
                    imo = update.imo ?: base.imo,
                    shipType = update.shipType ?: base.shipType,
                    destination = update.destination ?: base.destination,
                    lastSeenMs = nowMs,
                )
            }
        }
    }

    /** Vessels with a position reported within the last [STALE_MS].
     *  Stale records (and long-dead position-less ones) are evicted, so
     *  the map stays bounded by the live fleet. */
    fun snapshot(nowMs: Long): List<VesselRecord> {
        val live = ArrayList<VesselRecord>()
        for ((mmsi, record) in records) {
            val hasPosition = record.lat != null && record.lon != null
            val stale = if (hasPosition) {
                nowMs - record.lastReportMs > STALE_MS
            } else {
                nowMs - record.lastSeenMs > STALE_MS
            }
            if (stale) {
                records.remove(mmsi, record)
            } else if (hasPosition) {
                live.add(record)
            }
        }
        return live
    }

    fun size(): Int = records.size

    companion object {
        /** Upstream `AISSTREAM_STALE_MS`: a vessel silent for 30 minutes
         *  is no longer live. */
        const val STALE_MS: Long = 30 * 60 * 1000L
    }
}
