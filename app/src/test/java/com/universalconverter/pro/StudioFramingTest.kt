package com.universalconverter.pro

import com.universalconverter.pro.ui.studio.StudioFraming
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan
import kotlin.math.sin
import kotlin.math.tan

class StudioFramingTest {

    private fun assertSphereFits(aspect: Float, fovDeg: Float) {
        val distance = StudioFraming.fitDistance(radius = 1f, verticalFovDeg = fovDeg, aspect = aspect)
        val halfVertical = Math.toRadians(fovDeg / 2.0)
        val halfHorizontal = atan(tan(halfVertical) * aspect)
        val tangent = sin(minOf(halfVertical, halfHorizontal))
        assertTrue(
            "distance $distance must keep the unit sphere inside its limiting half-FOV",
            distance * tangent >= 1f - 1e-4f,
        )
    }

    @Test
    fun unitSphereFitsAcrossScreenAspects() {
        for (aspect in listOf(1080f / 2400f, 9f / 16f, 1f, 16f / 9f, 21f / 9f)) {
            assertSphereFits(aspect, 35f)
        }
    }

    @Test
    fun narrowPortraitNeedsMoreDistanceThanLandscape() {
        val portrait = StudioFraming.fitDistance(1f, 35f, 1080f / 2400f)
        val landscape = StudioFraming.fitDistance(1f, 35f, 2400f / 1080f)
        assertTrue("portrait $portrait should frame farther out than landscape $landscape", portrait > landscape)
    }
}
