package com.godseyeview.layer.vessels

import org.junit.Assert.assertEquals
import org.junit.Test

class VesselTypesTest {
    @Test fun kindRanges() {
        assertEquals(VesselKind.CARGO, vesselKind(70))
        assertEquals(VesselKind.CARGO, vesselKind(79))
        assertEquals(VesselKind.TANKER, vesselKind(80))
        assertEquals(VesselKind.TANKER, vesselKind(89))
        assertEquals(VesselKind.PASSENGER, vesselKind(60))
        assertEquals(VesselKind.PASSENGER, vesselKind(69))
        assertEquals(VesselKind.HIGHSPEED, vesselKind(40))
        assertEquals(VesselKind.HIGHSPEED, vesselKind(49))
        assertEquals(VesselKind.OTHER, vesselKind(99))
        assertEquals(VesselKind.OTHER, vesselKind(0))
        assertEquals(VesselKind.OTHER, vesselKind(null))
    }

    @Test fun kindSingleCodes() {
        assertEquals(VesselKind.FISHING, vesselKind(30))
        assertEquals(VesselKind.TUG, vesselKind(31))
        assertEquals(VesselKind.TUG, vesselKind(32))
        assertEquals(VesselKind.TUG, vesselKind(50))
        assertEquals(VesselKind.TUG, vesselKind(52))
        assertEquals(VesselKind.TUG, vesselKind(53))
        assertEquals(VesselKind.TUG, vesselKind(54))
        assertEquals(VesselKind.SAILING, vesselKind(36))
        assertEquals(VesselKind.PLEASURE, vesselKind(37))
        assertEquals(VesselKind.MILITARY, vesselKind(35))
        assertEquals(VesselKind.MILITARY, vesselKind(51))
        assertEquals(VesselKind.MILITARY, vesselKind(55))
    }

    @Test fun kindColoursMatchUpstream() {
        assertEquals("#39d5ff", VesselKind.CARGO.colorHex)
        assertEquals("#ffb347", VesselKind.TANKER.colorHex)
        assertEquals("#ff7adf", VesselKind.PASSENGER.colorHex)
        assertEquals("#c6ff3d", VesselKind.HIGHSPEED.colorHex)
        assertEquals("#7cff9b", VesselKind.FISHING.colorHex)
        assertEquals("#f7f0a3", VesselKind.TUG.colorHex)
        assertEquals("#b49cff", VesselKind.SAILING.colorHex)
        assertEquals("#ff6f61", VesselKind.PLEASURE.colorHex)
        assertEquals("#a9bccb", VesselKind.MILITARY.colorHex)
        assertEquals("#39d5ff", VesselKind.OTHER.colorHex)
    }

    @Test fun labelsPreferSpecificCodes() {
        assertEquals("FISHING", vesselTypeLabel(30))
        assertEquals("TOWING", vesselTypeLabel(31))
        assertEquals("TOWING", vesselTypeLabel(32))
        assertEquals("DREDGING", vesselTypeLabel(33))
        assertEquals("DIVE OPS", vesselTypeLabel(34))
        assertEquals("SAILING", vesselTypeLabel(36))
        assertEquals("PLEASURE", vesselTypeLabel(37))
        assertEquals("PILOT", vesselTypeLabel(50))
        assertEquals("SAR", vesselTypeLabel(51))
        assertEquals("TUG", vesselTypeLabel(52))
        assertEquals("LAW ENFORCE", vesselTypeLabel(55))
        assertEquals("MEDICAL", vesselTypeLabel(58))
    }

    @Test fun labelsFallBackToKind() {
        assertEquals("CARGO", vesselTypeLabel(71))
        assertEquals("TANKER", vesselTypeLabel(85))
        assertEquals("HIGH-SPEED", vesselTypeLabel(41))
        assertEquals("TUG", vesselTypeLabel(53))
        assertEquals("OTHER", vesselTypeLabel(20))
        assertEquals("OTHER", vesselTypeLabel(null))
    }
}
