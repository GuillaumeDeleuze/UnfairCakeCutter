package com.unfaircake.cutter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapeTransformTest {

    private fun assertVec(expected: Vec2, actual: Vec2, eps: Float = 1e-3f) {
        assertEquals(expected.x, actual.x, eps)
        assertEquals(expected.y, actual.y, eps)
    }

    @Test
    fun frameRoundTrip() {
        val t = ShapeTransform(cx = 0.3f, cy = 0.6f, width = 0.5f, height = 0.25f, rotationDeg = 30f)
        val back = t.toFrame(1080f, 1400f).toTransform(1080f, 1400f)
        assertEquals(t.cx, back.cx, 1e-5f)
        assertEquals(t.cy, back.cy, 1e-5f)
        assertEquals(t.width, back.width, 1e-5f)
        assertEquals(t.height, back.height, 1e-5f)
        assertEquals(t.rotationDeg, back.rotationDeg, 1e-5f)
    }

    @Test
    fun localWorldRoundTripAndContains() {
        val f = ShapeFrame(Vec2(500f, 400f), 400f, 100f, 90f)
        val p = Vec2(510f, 560f)
        assertVec(p, f.toWorld(f.toLocal(p)))
        // Rotated 90°, the long side is now vertical.
        assertTrue(f.contains(Vec2(500f, 590f), round = false))
        assertFalse(f.contains(Vec2(690f, 400f), round = false))
        assertTrue(f.contains(Vec2(500f, 590f), round = true))
        assertFalse(f.contains(Vec2(540f, 580f), round = true))
    }

    @Test
    fun cornerResizeKeepsOppositeCorner() {
        val f = ShapeFrame(Vec2(500f, 500f), 200f, 100f, 25f)
        val corners = f.corners()
        val opposite = corners[0]
        val dragged = corners[2] + Vec2(40f, 60f)
        val r = f.resizedFromCorner(opposite, dragged, minSide = 10f)
        assertVec(opposite, r.corners()[0])
        assertVec(dragged, r.corners()[2])
        assertEquals(25f, r.rotationDeg, 0f)
    }

    @Test
    fun pinchAroundCentroid() {
        val f = ShapeFrame(Vec2(100f, 100f), 100f, 50f, 0f)
        val z = f.transformed(Vec2.Zero, 2f, 0f, Vec2(0f, 0f))
        assertVec(Vec2(200f, 200f), z.center)
        assertEquals(200f, z.width, 1e-4f)
        assertEquals(100f, z.height, 1e-4f)
        val r = f.transformed(Vec2(5f, 0f), 1f, 90f, Vec2(0f, 0f))
        assertVec(Vec2(-95f, 100f), r.center)
        assertEquals(90f, r.rotationDeg, 1e-4f)
        // Zoom is clamped and keeps the aspect ratio.
        val big = f.transformed(Vec2.Zero, 10f, 0f, f.center, minSide = 10f, maxSide = 300f)
        assertEquals(300f, big.width, 1e-3f)
        assertEquals(150f, big.height, 1e-3f)
    }

    @Test
    fun hitCornerFindsNearest() {
        val f = ShapeFrame(Vec2(0f, 0f), 100f, 100f, 0f)
        assertEquals(2, f.hitCorner(Vec2(52f, 49f), 20f))
        assertNull(f.hitCorner(Vec2(0f, 0f), 20f))
    }

    @Test
    fun sizeSliderScalesBothSides() {
        val t = ShapeTransform(width = 0.8f, height = 0.4f).withSize(0.4f)
        assertEquals(0.4f, t.width, 1e-6f)
        assertEquals(0.2f, t.height, 1e-6f)
        assertEquals(-170f, ShapeTransform.normalizeDegrees(190f), 1e-6f)
        assertEquals(170f, ShapeTransform.normalizeDegrees(-190f), 1e-6f)
    }
}
