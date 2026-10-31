package com.universalconverter.pro.ui.studio

import kotlin.math.atan
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * Perspective distance that keeps a bounding sphere of [radius] inside the camera frustum
 * for the narrowest screen axis, independent of orbit angle.
 */
object StudioFraming {

    fun fitDistance(
        radius: Float,
        verticalFovDeg: Float,
        aspect: Float,
        margin: Float = 1.12f,
    ): Float {
        val safeRadius = radius.coerceAtLeast(1e-4f)
        val halfVertical = Math.toRadians(verticalFovDeg.coerceIn(10f, 120f).toDouble() / 2.0)
        val halfHorizontal = atan(tan(halfVertical) * aspect.coerceAtLeast(1e-3f))
        val limitingHalfFov = min(halfVertical, halfHorizontal)
        return (safeRadius / sin(limitingHalfFov) * margin).toFloat()
    }
}
