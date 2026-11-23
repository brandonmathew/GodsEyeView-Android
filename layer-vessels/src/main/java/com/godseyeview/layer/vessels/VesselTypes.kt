package com.godseyeview.layer.vessels

/** Vessel kinds by AIS ship-type code (ITU-R M.1371), ported from
 *  upstream `src/data/vesselTypes.js` (`vesselKind` + `VESSEL_KIND_STYLES`).
 *  Colours are the upstream kind colours, drawn on dark water. */
enum class VesselKind(val colorHex: String, val label: String) {
    CARGO("#39d5ff", "CARGO"),
    TANKER("#ffb347", "TANKER"),
    PASSENGER("#ff7adf", "PASSENGER"),
    HIGHSPEED("#c6ff3d", "HIGH-SPEED"),
    FISHING("#7cff9b", "FISHING"),
    TUG("#f7f0a3", "TUG"),
    SAILING("#b49cff", "SAILING"),
    PLEASURE("#ff6f61", "PLEASURE"),
    MILITARY("#a9bccb", "MILITARY"),
    OTHER("#39d5ff", "OTHER"),
}

/** Kind for an AIS ship-type code; null / unknown codes are [VesselKind.OTHER].
 *  Ranges follow upstream: cargo 70–79, tanker 80–89, passenger 60–69,
 *  high-speed 40–49; single codes: fishing 30, tug 31/32/50/52/53/54,
 *  sailing 36, pleasure 37, military 35/51/55 (35 military ops, 51 SAR,
 *  55 law enforcement — upstream groups them with military). */
fun vesselKind(shipType: Int?): VesselKind = when (shipType) {
    null -> VesselKind.OTHER
    30 -> VesselKind.FISHING
    31, 32, 50, 52, 53, 54 -> VesselKind.TUG
    35, 51, 55 -> VesselKind.MILITARY
    36 -> VesselKind.SAILING
    37 -> VesselKind.PLEASURE
    in 40..49 -> VesselKind.HIGHSPEED
    in 60..69 -> VesselKind.PASSENGER
    in 70..79 -> VesselKind.CARGO
    in 80..89 -> VesselKind.TANKER
    else -> VesselKind.OTHER
}

/** Display labels for ship-type codes that name something more specific
 *  than their kind (upstream `NUMERIC_TYPE_SPECIALS`). */
private val SPECIFIC_LABELS: Map<Int, String> = mapOf(
    30 to "FISHING",
    31 to "TOWING",
    32 to "TOWING",
    33 to "DREDGING",
    34 to "DIVE OPS",
    36 to "SAILING",
    37 to "PLEASURE",
    50 to "PILOT",
    51 to "SAR",
    52 to "TUG",
    55 to "LAW ENFORCE",
    58 to "MEDICAL",
)

/** Card display label for a ship-type code: the specific label where the
 *  code is specific, otherwise the kind label ("OTHER" when unknown). */
fun vesselTypeLabel(shipType: Int?): String =
    shipType?.let { SPECIFIC_LABELS[it] } ?: vesselKind(shipType).label
