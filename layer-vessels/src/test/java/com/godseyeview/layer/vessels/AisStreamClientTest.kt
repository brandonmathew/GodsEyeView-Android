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

    /** Q25 (issue #40): a wrapping cap produces a two-box subscription. */
    @Test fun subscriptionJsonWithViewportBoxes() {
        val boxes = com.godseyeview.layerapi.VisibleCap(0.0, 179.0, 5.0).boundingBoxes()
        assertEquals(2, boxes.size)
        val root = Json.parseToJsonElement(
            subscriptionJson("test-key-123", boxes),
        ).jsonObject
        val wire = root["BoundingBoxes"]!!.jsonArray
        assertEquals(2, wire.size)
        val first = wire[0].jsonArray
        assertEquals(180, first[1].jsonArray[1].jsonPrimitive.content.toInt())
        val second = wire[1].jsonArray
        assertEquals(-180, second[0].jsonArray[1].jsonPrimitive.content.toInt())
    }

    /** Q25: the client's box state starts global, narrows on update,
     *  and an identical update is a no-op. */
    @Test fun updateBoundingBoxesState() {
        val client = AisStreamClient(VesselStore())
        assertEquals(
            listOf(com.godseyeview.layerapi.LatLonBox.GLOBAL),
            client.boundingBoxes,
        )
        val regional = com.godseyeview.layerapi.VisibleCap(50.0, 10.0, 10.0)
            .boundingBoxes()
        client.updateBoundingBoxes(regional)
        assertEquals(regional, client.boundingBoxes)
        client.updateBoundingBoxes(regional)
        assertEquals(regional, client.boundingBoxes)
        client.updateBoundingBoxes(emptyList())
        assertEquals(
            listOf(com.godseyeview.layerapi.LatLonBox.GLOBAL),
            client.boundingBoxes,
        )
    }
}
