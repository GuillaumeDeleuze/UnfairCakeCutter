package com.unfaircake.cutter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class SnapFitTest {

    private val w = 320
    private val h = 240

    /** Filled ellipse (or rectangle) centred at (cx, cy), half sides a and b, turned by deg. */
    private fun mask(
        cx: Double, cy: Double, a: Double, b: Double, deg: Double, round: Boolean,
        into: BooleanArray = BooleanArray(w * h),
    ): BooleanArray {
        val t = deg * PI / 180.0
        for (y in 0 until h) for (x in 0 until w) {
            val dx = x + 0.5 - cx
            val dy = y + 0.5 - cy
            val u = dx * cos(t) + dy * sin(t)
            val v = -dx * sin(t) + dy * cos(t)
            val inside = if (round) (u / a) * (u / a) + (v / b) * (v / b) <= 1.0 else abs(u) <= a && abs(v) <= b
            if (inside) into[y * w + x] = true
        }
        return into
    }

    private fun angleClose(expected: Double, actual: Double, tol: Double) {
        var d = (actual - expected) % 180.0
        if (d > 90) d -= 180
        if (d < -90) d += 180
        assertTrue("angle $actual vs $expected", abs(d) <= tol)
    }

    @Test
    fun findsATiltedOval() {
        val m = mask(150.0, 110.0, 90.0, 55.0, 20.0, round = true)
        val r = SnapFit.fromMask(m, w, h, 150, 110)
        assertNotNull(r); r!!
        assertTrue(r.round)
        assertEquals(150.0, r.cx, 1.5)
        assertEquals(110.0, r.cy, 1.5)
        assertEquals(180.0, r.width, 4.0)
        assertEquals(110.0, r.height, 4.0)
        angleClose(20.0, r.rotationDeg, 2.0)
    }

    @Test
    fun findsATiltedTray() {
        val m = mask(170.0, 120.0, 100.0, 60.0, -12.0, round = false)
        val r = SnapFit.fromMask(m, w, h, 200, 130)!!
        assertFalse(r.round)
        assertEquals(170.0, r.cx, 1.5)
        assertEquals(120.0, r.cy, 1.5)
        assertEquals(200.0, r.width, 5.0)
        assertEquals(120.0, r.height, 5.0)
        angleClose(-12.0, r.rotationDeg, 2.0)
    }

    @Test
    fun rotationStaysWithinFortyFiveDegrees() {
        // A tray standing upright is reported as a wide tray turned by 0° once sides are swapped.
        val m = mask(160.0, 120.0, 40.0, 100.0, 0.0, round = false)
        val r = SnapFit.fromMask(m, w, h, 160, 120)!!
        assertTrue(abs(r.rotationDeg) <= 45.0)
        assertEquals(80.0, r.width, 4.0)
        assertEquals(200.0, r.height, 4.0)
    }

    @Test
    fun nearlyRoundCakeIsNotTurned() {
        val m = mask(160.0, 120.0, 80.0, 78.0, 37.0, round = true)
        val r = SnapFit.fromMask(m, w, h, 160, 120)!!
        assertTrue(r.round)
        assertEquals(0.0, r.rotationDeg, 0.0)
    }

    @Test
    fun keepsOnlyThePieceUnderTheFinger() {
        val m = mask(80.0, 120.0, 50.0, 50.0, 0.0, round = true)
        mask(250.0, 120.0, 40.0, 30.0, 0.0, round = false, into = m)
        val r = SnapFit.fromMask(m, w, h, 250, 125)!!
        assertFalse(r.round)
        assertEquals(250.0, r.cx, 1.5)
    }

    @Test
    fun fillsHolesLeftByDecorations() {
        val m = mask(160.0, 120.0, 90.0, 70.0, 0.0, round = true)
        // Sprinkles and a cherry the segmentation missed.
        for (y in 100..130) for (x in 140..175) m[y * w + x] = false
        val r = SnapFit.fromMask(m, w, h, 110, 120)!!
        assertTrue(r.round)
        assertEquals(160.0, r.cx, 1.5)
        assertEquals(120.0, r.cy, 1.5)
    }

    @Test
    fun snapsToAPieceJustNextToTheTap() {
        val m = mask(160.0, 120.0, 60.0, 60.0, 0.0, round = true)
        // Tap lands 5 px outside the edge.
        assertNotNull(SnapFit.fromMask(m, w, h, 225, 120))
    }

    @Test
    fun givesUpOnNothingTinyOrEverything() {
        assertNull(SnapFit.fromMask(BooleanArray(w * h), w, h, 160, 120))
        assertNull(SnapFit.fromMask(mask(160.0, 120.0, 4.0, 4.0, 0.0, round = true), w, h, 160, 120))
        assertNull(SnapFit.fromMask(BooleanArray(w * h) { true }, w, h, 160, 120))
    }

    @Test
    fun givesUpOnAShapelessBlob() {
        // An L-shaped spill is neither an oval nor a tray.
        val m = mask(110.0, 120.0, 20.0, 90.0, 0.0, round = false)
        mask(170.0, 195.0, 80.0, 15.0, 0.0, round = false, into = m)
        assertNull(SnapFit.fromMask(m, w, h, 110, 120))
    }

    @Test
    fun choosesTheCakeKind() {
        val oval = SnapFit.Result(round = true, cx = 0.0, cy = 0.0, width = 100.0, height = 60.0, rotationDeg = 0.0)
        assertEquals(CakeShape.ROUND, SnapFit.shapeFor(oval, CakeShape.LOG))

        val tray = oval.copy(round = false)
        assertEquals(CakeShape.TRAY_GRID, SnapFit.shapeFor(tray, CakeShape.ROUND))
        assertEquals(CakeShape.TRAY_GRID, SnapFit.shapeFor(tray, CakeShape.LOG))
        assertEquals(CakeShape.TRAY_STRIPS, SnapFit.shapeFor(tray, CakeShape.TRAY_STRIPS))

        val log = tray.copy(width = 40.0, height = 150.0)
        assertEquals(CakeShape.LOG, SnapFit.shapeFor(log, CakeShape.TRAY_GRID))
    }

    @Test
    fun convertsToTheOverlayFrame() {
        val r = SnapFit.Result(round = true, cx = 200.0, cy = 150.0, width = 300.0, height = 180.0, rotationDeg = 10.0)
        val t = SnapFit.toTransform(r, canvasWidth = 400f, canvasHeight = 600f)
        assertEquals(0.5f, t.cx, 1e-4f)
        assertEquals(0.25f, t.cy, 1e-4f)
        assertEquals(0.75f, t.width, 1e-4f)
        assertEquals(0.45f, t.height, 1e-4f)
        assertEquals(10f, t.rotationDeg, 1e-4f)
    }

    @Test
    fun tableAroundThePlateIsNotACake() {
        // Tapping the table: the piece is everything but the plate, touching every edge.
        val m = BooleanArray(w * h) { true }
        val plate = mask(160.0, 120.0, 100.0, 70.0, 0.0, round = true)
        for (i in m.indices) if (plate[i]) m[i] = false
        // Segmentation keeps a thin band of "sure table" along the edges out.
        for (y in 0 until h) for (x in 0 until w) if (x < 6 || y < 6 || x >= w - 6 || y >= h - 6) m[y * w + x] = false
        assertNull(SnapFit.fromMask(m, w, h, 20, 20))
    }

    @Test
    fun cakeCutOffByOneEdgeStillSnaps() {
        // Close-up: the cake runs off the right edge.
        val m = mask(290.0, 120.0, 110.0, 80.0, 0.0, round = true)
        assertNotNull(SnapFit.fromMask(m, w, h, 250, 120))
    }

    @Test
    fun cornerOfTheTableIsNotACake() {
        val m = mask(0.0, 0.0, 60.0, 70.0, 0.0, round = false)
        assertNull(SnapFit.fromMask(m, w, h, 20, 25))
    }

    @Test
    fun cakeFillingTheWidthStillSnaps() {
        // Close-up: the cake spills off both the left and the right edge.
        val m = mask(160.0, 120.0, 175.0, 80.0, 0.0, round = true)
        assertNotNull(SnapFit.fromMask(m, w, h, 160, 120))
    }

    /** The outline back in mask pixels. */
    private fun outlineInMask(r: SnapFit.Result): List<Pt> {
        val t = r.rotationDeg * PI / 180.0
        return r.outline.map { p ->
            val u = p.x * r.width
            val v = p.y * r.height
            Pt(r.cx + u * cos(t) - v * sin(t), r.cy + u * sin(t) + v * cos(t))
        }
    }

    @Test
    fun outlineFollowsTheCake() {
        // A wonky pizza: circle with a bumpy rim.
        val m = BooleanArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val dx = x + 0.5 - 160
            val dy = y + 0.5 - 120
            val t = kotlin.math.atan2(dy, dx)
            val r = 80 + 8 * sin(5 * t)
            if (dx * dx + dy * dy <= r * r) m[y * w + x] = true
        }
        val r = SnapFit.fromMask(m, w, h, 160, 120)!!
        assertTrue(r.round)
        assertTrue("points ${r.outline.size}", r.outline.size in 12..160)
        // Local outline fits its unit box.
        assertTrue(r.outline.all { abs(it.x) <= 0.5001 && abs(it.y) <= 0.5001 })
        val poly = outlineInMask(r)
        assertEquals(m.count { it }.toDouble(), Freeform.area(poly), 0.03 * m.count { it })
        // The bumps survive: the outline is not an ellipse.
        val c = Freeform.centroid(poly)
        val reaches = (0 until 72).map { Freeform.reach(poly, c, 2 * PI * it / 72) }
        assertTrue(reaches.max() - reaches.min() > 10.0)
    }

    @Test
    fun trayOutlineHasStraightSidesAndRightAngle() {
        val m = mask(170.0, 120.0, 100.0, 60.0, -12.0, round = false)
        val r = SnapFit.fromMask(m, w, h, 200, 130)!!
        assertFalse(r.round)
        assertEquals(1.0, Freeform.rectangularity(r.outline), 0.05)
    }

    @Test
    fun roundCakeWithItsSideShowingIsStillRound() {
        // Seen from across the table: the top oval plus the side band below it.
        val m = mask(160.0, 110.0, 110.0, 60.0, 0.0, round = true)
        mask(160.0, 135.0, 110.0, 25.0, 0.0, round = false, into = m)
        mask(160.0, 160.0, 110.0, 60.0, 0.0, round = true, into = m)
        val r = SnapFit.fromMask(m, w, h, 160, 120)!!
        assertTrue("round", r.round)
    }

    @Test
    fun liveSnapsKeepTheirOrientation() {
        // A squarish cake read at +44° after -44°: the same frame turned a quarter.
        val r = SnapFit.Result(
            round = false, cx = 100.0, cy = 80.0, width = 60.0, height = 40.0, rotationDeg = 44.0,
            outline = listOf(Pt(-0.5, -0.5), Pt(0.5, -0.5), Pt(0.5, 0.5), Pt(-0.2, 0.5)),
        )
        val a = SnapFit.alignTo(r, previousRotationDeg = -44.0)
        assertEquals(-46.0, a.rotationDeg, 1e-9)
        assertEquals(40.0, a.width, 1e-9)
        assertEquals(60.0, a.height, 1e-9)
        // Same outline in the image: every point lands where it was.
        fun world(q: SnapFit.Result) = q.outline.map { p ->
            val t = q.rotationDeg * PI / 180.0
            val u = p.x * q.width
            val v = p.y * q.height
            Pt(q.cx + u * cos(t) - v * sin(t), q.cy + u * sin(t) + v * cos(t))
        }
        world(r).zip(world(a)).forEach { (p, q) ->
            assertEquals(p.x, q.x, 1e-9)
            assertEquals(p.y, q.y, 1e-9)
        }
        // Already close: untouched.
        assertEquals(r, SnapFit.alignTo(r, previousRotationDeg = 30.0))
    }

    @Test
    fun reframeKeepsTheOutlineAndTakesTheAngle() {
        val m = mask(160.0, 120.0, 80.0, 60.0, 0.0, round = false)
        val r = SnapFit.fromMask(m, w, h, 160, 120)!!
        val turned = SnapFit.reframe(r, 20.0)
        assertEquals(20.0, turned.rotationDeg, 1e-9)
        assertTrue(turned.outline.all { abs(it.x) <= 0.5001 && abs(it.y) <= 0.5001 })
        assertEquals(Freeform.area(outlineInMask(r)), Freeform.area(outlineInMask(turned)), 1e-6)
        val a = Freeform.centroid(outlineInMask(r))
        val b = Freeform.centroid(outlineInMask(turned))
        assertEquals(a.x, b.x, 1e-6)
        assertEquals(a.y, b.y, 1e-6)
    }

    @Test
    fun squarishRoundCakeIsNotTurned() {
        val m = mask(160.0, 120.0, 80.0, 70.0, 30.0, round = true)
        val r = SnapFit.fromMask(m, w, h, 160, 120)!!
        assertEquals(0.0, r.rotationDeg, 0.0)
    }

    @Test
    fun aSecondTapAddsTheCrust() {
        // First tap found the topping; the second found the crust ring, a pixel apart.
        val topping = mask(160.0, 120.0, 70.0, 70.0, 0.0, round = true)
        val crust = mask(160.0, 120.0, 90.0, 90.0, 0.0, round = true)
        val inner = mask(160.0, 120.0, 71.5, 71.5, 0.0, round = true)
        for (i in crust.indices) if (inner[i]) crust[i] = false
        val merged = SnapFit.merge(topping, crust, w, h)
        val r = SnapFit.fromMask(merged, w, h, 160, 120)!!
        assertEquals(180.0, r.width, 4.0)
        assertEquals(180.0, r.height, 4.0)
    }
}
