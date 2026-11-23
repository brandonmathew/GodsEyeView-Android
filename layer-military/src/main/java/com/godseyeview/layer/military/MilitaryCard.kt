package com.godseyeview.layer.military

import com.godseyeview.layerapi.CardField
import com.godseyeview.layerapi.adsb.AdsbRecord
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Detail-card fields for one military flight, in display order. Only
 * fields with a known value are emitted (Status is always known).
 * Local to this module — layers never depend on another layer's module,
 * so this mirrors the flights layer's formatting rather than sharing it.
 */
internal object MilitaryCard {

    fun fieldsFor(record: AdsbRecord): List<CardField> {
        val fields = mutableListOf<CardField>()
        fields += CardField("ICAO24", record.id.uppercase(Locale.US))
        record.originCountry?.let { fields += CardField("Origin", it) }
        record.typeCode?.let { fields += CardField("Type", it) }
        record.registration?.let { fields += CardField("Registration", it) }
        record.operator?.let { fields += CardField("Operator", it) }

        val altitudeM = record.baroAltitudeM ?: record.geoAltitudeM
        altitudeM?.let {
            val metres = it.roundToLong()
            val feet = (it / 0.3048).roundToLong()
            fields += CardField(
                "Altitude",
                String.format(Locale.US, "%,d m · %,d ft", metres, feet),
            )
        }
        record.speedMps?.let {
            val kmh = (it * 3.6).roundToLong()
            val knots = (it / 0.514444).roundToLong()
            fields += CardField(
                "Speed",
                String.format(Locale.US, "%,d km/h · %,d kt", kmh, knots),
            )
        }
        record.courseDeg?.let {
            fields += CardField("Heading", String.format(Locale.US, "%d°", it.roundToInt()))
        }
        record.verticalRateMps?.let {
            fields += CardField(
                "Vertical rate",
                String.format(Locale.US, "%+.1f m/s", it),
            )
        }
        record.squawk?.let { fields += CardField("Squawk", it) }
        fields += CardField("Status", if (record.onGround) "On ground" else "Airborne")
        return fields
    }
}
