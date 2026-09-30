package com.unfaircake.cutter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.random.Random

class GeometryTest {

    private val random = Random(7)

    private fun randomShares(n: Int, u: Int = 80) = Shares.compute(Shares.newSeeds(n, random), u)

    @Test
    fun wedgesCoverTheCircleAndFollowShares() {
        for (n in 1..12) {
            val shares = randomShares(n)
            val wedges = CakeGeometry.wedges(shares)
            assertEquals(n, wedges.size)
            assertEquals(2 * PI, wedges.sumOf { it.sweep }, 1e-9)
            assertEquals(CakeGeometry.START_ANGLE, wedges.first().start, 1e-12)
            // Ellipse sector areas stay proportional to the shares for any half-axes.
            val a = 3.0
            val b = 1.25
            val ellipseArea = PI * a * b
            wedges.forEachIndexed { i, w ->
                assertEquals(shares[i] * ellipseArea, CakeGeometry.ellipseSectorArea(w.sweep, a, b), 1e-9)
                if (i > 0) assertEquals(wedges[i - 1].start + wedges[i - 1].sweep, w.start, 1e-12)
            }
        }
    }

    @Test
    fun stripsFollowSharesAlongTheLongSide() {
        for (n in 1..12) {
            val shares = randomShares(n)
            for ((w, h) in listOf(400.0 to 100.0, 120.0 to 300.0)) {
                val strips = CakeGeometry.strips(shares, w, h)
                assertEquals(n, strips.size)
                strips.forEachIndexed { i, r ->
                    assertEquals(shares[i] * w * h, r.area, 1e-6)
                    if (w >= h) assertEquals(h, r.height, 1e-9) else assertEquals(w, r.width, 1e-9)
                }
                val last = strips.last()
                if (w >= h) assertEquals(w / 2, last.right, 1e-9) else assertEquals(h / 2, last.bottom, 1e-9)
            }
        }
    }

    @Test
    fun treemapAreasMatchSharesAndTileTheBox() {
        for (n in 1..12) {
            repeat(20) {
                val shares = randomShares(n, u = random.nextInt(0, 101))
                val w = random.nextDouble(50.0, 500.0)
                val h = random.nextDouble(50.0, 500.0)
                val pieces = CakeGeometry.squarified(shares, w, h)
                assertEquals(n, pieces.size)
                pieces.forEachIndexed { i, r ->
                    assertEquals("n=$n i=$i", shares[i] * w * h, r.area, 1e-6 * w * h)
                    assertTrue(r.left >= -w / 2 - 1e-6 && r.right <= w / 2 + 1e-6)
                    assertTrue(r.top >= -h / 2 - 1e-6 && r.bottom <= h / 2 + 1e-6)
                }
                assertEquals(w * h, pieces.sumOf { it.area }, 1e-6 * w * h)
                // No two pieces overlap.
                for (i in pieces.indices) for (j in i + 1 until pieces.size) {
                    val a = pieces[i]
                    val b = pieces[j]
                    val ox = minOf(a.right, b.right) - maxOf(a.left, b.left)
                    val oy = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
                    assertTrue("overlap $i/$j", ox <= 1e-6 || oy <= 1e-6)
                }
            }
        }
    }

    @Test
    fun treemapPiecesAreNearSquareForEqualShares() {
        val pieces = CakeGeometry.squarified(List(6) { 1.0 / 6 }, 300.0, 200.0)
        pieces.forEach { assertTrue("aspect ${CakeGeometry.aspect(it)}", CakeGeometry.aspect(it) < 2.0) }
        val strips = CakeGeometry.strips(List(6) { 1.0 / 6 }, 300.0, 200.0)
        assertTrue(pieces.maxOf { CakeGeometry.aspect(it) } < strips.maxOf { CakeGeometry.aspect(it) })
    }
}
