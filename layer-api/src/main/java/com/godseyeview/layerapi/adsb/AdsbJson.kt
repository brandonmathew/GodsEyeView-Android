package com.godseyeview.layerapi.adsb

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/** Small JSON accessors shared by the ADS-B parsers. */

internal fun JsonElement?.asPrimitive(): JsonPrimitive? =
    this as? JsonPrimitive

/** Finite number from a JSON number (or numeric string), else null. */
internal fun JsonElement?.finiteDouble(): Double? {
    val prim = asPrimitive() ?: return null
    if (prim is JsonNull || prim.isString && prim.content.isBlank()) return null
    val value = prim.doubleOrNull ?: return null
    return value.takeIf { it.isFinite() }
}

/** Trimmed text from a JSON string, else null when blank/absent/non-string. */
internal fun JsonElement?.cleanTextOrNull(): String? {
    val prim = asPrimitive() ?: return null
    if (prim is JsonNull || !prim.isString) return null
    return prim.content.trim().takeIf { it.isNotEmpty() }
}

/**
 * Raw text of a JSON string or number (used for squawk / category, which
 * arrive as strings but are cheap to accept as numbers too), trimmed;
 * null when blank/absent.
 */
internal fun JsonElement?.rawTextOrNull(): String? {
    val prim = asPrimitive() ?: return null
    if (prim is JsonNull) return null
    if (prim.isString || !prim.content.isNullOrBlank()) {
        return prim.content.trim().takeIf { it.isNotEmpty() }
    }
    return null
}

internal fun JsonElement?.booleanOrNullValue(): Boolean? =
    asPrimitive()?.booleanOrNull
