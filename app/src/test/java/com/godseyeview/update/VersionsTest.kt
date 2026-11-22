package com.godseyeview.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsTest {
    @Test
    fun coreNumbersCompareNumerically() {
        assertTrue(Versions.compare("1.0.1", "1.0.0") > 0)
        assertTrue(Versions.compare("1.10.0", "1.9.0") > 0)
        assertEquals(0, Versions.compare("1.0.0", "1.0.0"))
    }

    @Test
    fun releaseOutranksItsPrereleases() {
        assertTrue(Versions.isNewer("1.0.0", "1.0.0-beta.23"))
        assertFalse(Versions.isNewer("1.0.0-beta.23", "1.0.0"))
    }

    @Test
    fun betaNumbersCompareNumerically() {
        assertTrue(Versions.isNewer("1.0.0-beta.23", "1.0.0-beta.9"))
        assertFalse(Versions.isNewer("1.0.0-beta.9", "1.0.0-beta.23"))
        assertFalse(Versions.isNewer("1.0.0-beta.23", "1.0.0-beta.23"))
    }

    @Test
    fun higherCoreBeatsPrereleaseOfLowerCore() {
        assertTrue(Versions.isNewer("1.0.1-beta.1", "1.0.0"))
        assertTrue(Versions.isNewer("2.0.0-beta.1", "1.0.0"))
    }
}
