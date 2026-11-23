package com.godseyeview.layer.vessels

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class AisStreamClientTest {
    @Test fun subscriptionJsonShape() {
        val root = Json.parseToJsonElement(subscriptionJson("test-key-123")).jsonObject
        assertEquals("test-key-123", root["APIKey"]!!.jsonPrimitive.content)

        val boxes = root["BoundingBoxes"]!!.jsonArray
        assertEquals(1, boxes.size)
        val box = boxes[0].jsonArray
        val sw = box[0].jsonArray
        val ne = box[1].jsonArray
        assertEquals(-90, sw[0].jsonPrimitive.content.toInt())
        assertEquals(-180, sw[1].jsonPrimitive.content.toInt())
        assertEquals(90, ne[0].jsonPrimitive.content.toInt())
        assertEquals(180, ne[1].jsonPrimitive.content.toInt())

        val types = root["FilterMessageTypes"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(
            listOf(
                "PositionReport",
                "StandardClassBPositionReport",
                "ExtendedClassBPositionReport",
                "ShipStaticData",
                "StaticDataReport",
            ),
            types,
        )
    }
}
