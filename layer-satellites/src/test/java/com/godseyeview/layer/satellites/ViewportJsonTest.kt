package com.godseyeview.layer.satellites

import com.godseyeview.layerapi.VisibleCap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Q25 (issue #40): the viewport message pushed to satellites.js. */
class ViewportJsonTest {

    @Test
    fun `null cap is the global message`() {
        val root = Json.parseToJsonElement(buildViewportJson(null)).jsonObject
        assertEquals("viewport", root["type"]!!.jsonPrimitive.content)
        assertTrue(root["global"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `hemisphere cap is the global message`() {
        val root = Json.parseToJsonElement(buildViewportJson(VisibleCap.GLOBAL)).jsonObject
        assertTrue(root["global"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `sub hemisphere cap carries centre and radius`() {
        val root = Json.parseToJsonElement(
            buildViewportJson(VisibleCap(21.3, -157.8, 17.0)),
        ).jsonObject
        assertEquals(21.3, root["lat"]!!.jsonPrimitive.double, 1e-9)
        assertEquals(-157.8, root["lon"]!!.jsonPrimitive.double, 1e-9)
        assertEquals(17.0, root["radiusDeg"]!!.jsonPrimitive.double, 1e-9)
    }
}
