package com.unfaircake.cutter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class FreeformTest {

    /** A wobbly, pizza-like outline: a circle with a bumpy rim, off-centre on purpose. */
    private val pizza: List<Pt> = List(90) { i ->
        val t = 2 * PI * i / 90
        val r = 0.42 + 0.04 * sin(5 * t) + 0.02 * cos(3 * t)
        Pt(0.03 + r * cos(t), -0.02 + r * sin(t))
    }

    private val square = listOf(Pt(-0.5, -0.5), Pt(0.5, -0.5), Pt(0.5, 0.5), Pt(-0.5, 0.5))

    private val shares = listOf(0.31, 0.07, 0.22, 0.15, 0.25)

    /** Area of [poly] inside the sector [start, start+sweep] around [c], by fine sampling. */
    private fun sectorArea(poly: List<Pt>, c: Pt, start: Double, sweep: Double): Double {
        // Split into thin convex sectors and clip the polygon by both of their edges.
        val steps = 64
        var sum = 0.0
        for (k in 0 until steps) {
            val a0 = start + sweep * k / steps
            val a1 = start + sweep * (k + 1) / steps
            // Keep points left of ray a0 and right of ray a1 (screen: y down, angles clockwise).
            var p = Freeform.clip(poly, sin(a0), -cos(a0), sin(a0) * c.x - cos(a0) * c.y)
            p = Freeform.clip(p, -sin(a1), cos(a1), -sin(a1) * c.x + cos(a1) * c.y)
            sum += Freeform.area(p)
        }
        return sum
    }

    @Test
    fun areaAndCentroidOfASquare() {
        assertEquals(1.0, Freeform.area(square), 1e-12)
        val c = Freeform.centroid(square)
        assertEquals(0.0, c.x, 1e-12)
        assertEquals(0.0, c.y, 1e-12)
    }

    @Test
    fun wedgesSplitTheAreaByShare() {
        val total = Freeform.area(pizza)
        val c = Freeform.centroid(pizza)
        val wedges = Freeform.wedges(pizza, shares)
        assertEquals(shares.size, wedges.size)
        assertEquals(2 * PI, wedges.sumOf { it.sweep }, 1e-9)
        wedges.forEachIndexed { i, w ->
            assertEquals("wedge $i", shares[i] * total, sectorArea(pizza, c, w.start, w.sweep), 0.004 * total)
        }
    }

    @Test
    fun stripsSplitTheAreaByShare() {
        val total = Freeform.area(pizza)
        val pieces = Freeform.strips(pizza, shares, alongX = true)
        assertEquals(shares.size, pieces.size)
        pieces.forEachIndexed { i, p -> assertEquals("strip $i", shares[i] * total, Freeform.area(p), 1e-6) }
        // Left to right, in person order.
        val xs = pieces.map { Freeform.centroid(it).x }
        assertEquals(xs.sorted(), xs)
    }

    @Test
    fun gridSplitsTheAreaByShareIntoChunkyPieces() {
        val total = Freeform.area(pizza)
        val pieces = Freeform.grid(pizza, shares)
        assertEquals(shares.size, pieces.size)
        pieces.forEachIndexed { i, p -> assertEquals("piece $i", shares[i] * total, Freeform.area(p), 1e-6) }
        assertEquals(total, pieces.sumOf { Freeform.area(it) }, 1e-6)
    }

    @Test
    fun onePersonGetsTheWholeCake() {
        assertEquals(1, Freeform.grid(pizza, listOf(1.0)).size)
        assertEquals(Freeform.area(pizza), Freeform.area(Freeform.strips(pizza, listOf(1.0), true)[0]), 1e-9)
        assertEquals(2 * PI, Freeform.wedges(pizza, listOf(1.0)).single().sweep, 1e-9)
    }

    @Test
    fun rectangularityTellsTraysFromRounds() {
        val circle = List(64) { i -> Pt(cos(2 * PI * i / 64), sin(2 * PI * i / 64)) }
        assertEquals(PI / 4, Freeform.rectangularity(circle), 0.01)
        val tilted = square.map { Pt(it.x * cos(0.3) - it.y * sin(0.3), it.x * sin(0.3) + it.y * cos(0.3)) }
        assertEquals(1.0, Freeform.rectangularity(tilted), 1e-6)
        assertEquals(0.3, Freeform.minAreaRect(tilted).angle, 1e-6)
    }

    @Test
    fun simplifiesAJaggedEdge() {
        // A staircase along a straight line collapses to its ends.
        val stairs = (0..40).flatMap { i -> listOf(Pt(i.toDouble(), i.toDouble()), Pt(i + 1.0, i.toDouble())) }
        val simple = Freeform.simplify(stairs, epsilon = 1.0)
        assertTrue(simple.size <= 4)
    }
}
