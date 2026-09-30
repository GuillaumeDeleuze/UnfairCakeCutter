package com.unfaircake.cutter.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** A point in the cake's local frame. */
data class Pt(val x: Double, val y: Double)

/** Rotated bounding rectangle: centre, side lengths along its own axes, angle of its width axis. */
data class OrientedRect(val cx: Double, val cy: Double, val width: Double, val height: Double, val angle: Double) {
    val area: Double get() = width * height
}

/**
 * Slicing geometry for a cake of any outline (a snapped cake: a wonky pizza, a dish with round
 * corners). Outlines are simple polygons; every function returns one piece per share, in order,
 * and each piece's area is its share of the outline's area.
 */
object Freeform {

    fun area(poly: List<Pt>): Double = abs(signedArea(poly))

    private fun signedArea(poly: List<Pt>): Double {
        if (poly.size < 3) return 0.0
        var s = 0.0
        for (i in poly.indices) {
            val p = poly[i]
            val q = poly[(i + 1) % poly.size]
            s += p.x * q.y - q.x * p.y
        }
        return s / 2.0
    }

    fun centroid(poly: List<Pt>): Pt {
        val a = signedArea(poly)
        if (abs(a) < 1e-12) {
            return Pt(poly.sumOf { it.x } / max(1, poly.size), poly.sumOf { it.y } / max(1, poly.size))
        }
        var cx = 0.0
        var cy = 0.0
        for (i in poly.indices) {
            val p = poly[i]
            val q = poly[(i + 1) % poly.size]
            val f = p.x * q.y - q.x * p.y
            cx += (p.x + q.x) * f
            cy += (p.y + q.y) * f
        }
        return Pt(cx / (6 * a), cy / (6 * a))
    }

    /** The part of [poly] where nx·x + ny·y ≤ c (Sutherland–Hodgman against one half-plane). */
    fun clip(poly: List<Pt>, nx: Double, ny: Double, c: Double): List<Pt> {
        if (poly.isEmpty()) return poly
        val out = ArrayList<Pt>(poly.size + 4)
        for (i in poly.indices) {
            val p = poly[i]
            val q = poly[(i + 1) % poly.size]
            val dp = nx * p.x + ny * p.y - c
            val dq = nx * q.x + ny * q.y - c
            if (dp <= 0) out += p
            if ((dp < 0 && dq > 0) || (dp > 0 && dq < 0)) {
                val t = dp / (dp - dq)
                out += Pt(p.x + (q.x - p.x) * t, p.y + (q.y - p.y) * t)
            }
        }
        return out
    }

    // Wedges

    /** Rays sampled around the centre to measure how much cake lies in each direction. */
    private const val RAYS = 1440

    /**
     * Wedges from the outline's centroid, starting at 12 o'clock, with angles chosen so each
     * wedge holds its share of the area (not of the angle: a wonky cake has more cake in some
     * directions). Exact for outlines every point of which the centre can see, which cakes are.
     */
    fun wedges(poly: List<Pt>, shares: List<Double>): List<Wedge> {
        val total = shares.sum()
        if (shares.isEmpty() || total <= 0.0) return emptyList()
        if (shares.size == 1) return listOf(Wedge(CakeGeometry.START_ANGLE, 2 * PI))
        val c = centroid(poly)
        // Cumulative area swept from 12 o'clock, ray by ray.
        val start = CakeGeometry.START_ANGLE
        val step = 2 * PI / RAYS
        val r = DoubleArray(RAYS + 1) { k -> reach(poly, c, start + k * step) }
        val cumulative = DoubleArray(RAYS + 1)
        for (k in 0 until RAYS) cumulative[k + 1] = cumulative[k] + 0.5 * r[k] * r[k + 1] * sin(step)
        val full = cumulative[RAYS]

        val result = ArrayList<Wedge>(shares.size)
        var acc = 0.0
        var previous = start
        var k = 0
        shares.forEachIndexed { i, share ->
            acc += share / total
            val end = if (i == shares.lastIndex) {
                start + 2 * PI
            } else {
                val target = acc * full
                while (k < RAYS && cumulative[k + 1] < target) k++
                val span = cumulative[k + 1] - cumulative[k]
                val f = if (span > 0) (target - cumulative[k]) / span else 0.0
                start + (k + f) * step
            }
            result += Wedge(previous, end - previous)
            previous = end
        }
        return result
    }

    /** Distance from [c] to the outline's far edge along the direction [angle]. */
    fun reach(poly: List<Pt>, c: Pt, angle: Double): Double {
        val dx = cos(angle)
        val dy = sin(angle)
        var best = 0.0
        for (i in poly.indices) {
            val p = poly[i]
            val q = poly[(i + 1) % poly.size]
            val ex = q.x - p.x
            val ey = q.y - p.y
            val den = dx * ey - dy * ex
            if (abs(den) < 1e-15) continue
            val wx = p.x - c.x
            val wy = p.y - c.y
            val t = (wx * ey - wy * ex) / den
            val u = (wx * dy - wy * dx) / den
            if (t > 0 && u >= 0 && u <= 1) best = max(best, t)
        }
        return best
    }

    // Strips and grid

    /**
     * Parallel slices across [poly], cut perpendicular to x ([alongX]) or y, in person order,
     * each holding its share of the area.
     */
    fun strips(poly: List<Pt>, shares: List<Double>, alongX: Boolean): List<List<Pt>> {
        val total = shares.sum()
        if (shares.isEmpty() || total <= 0.0) return emptyList()
        val nx = if (alongX) 1.0 else 0.0
        val ny = if (alongX) 0.0 else 1.0
        val full = area(poly)
        val pieces = ArrayList<List<Pt>>(shares.size)
        var rest = poly
        var acc = 0.0
        shares.forEachIndexed { i, share ->
            if (i == shares.lastIndex) {
                pieces += rest
                return@forEachIndexed
            }
            acc += share / total
            val cut = cutAt(poly, nx, ny, acc * full)
            pieces += clip(rest, nx, ny, cut)
            rest = clip(rest, -nx, -ny, -cut)
        }
        return pieces
    }

    /**
     * Chunky pieces: the shares are split into two groups of about equal weight, the cake is cut
     * across its longer side in that proportion, and each half is cut again the same way.
     */
    fun grid(poly: List<Pt>, shares: List<Double>): List<List<Pt>> {
        val total = shares.sum()
        if (shares.isEmpty() || total <= 0.0) return emptyList()
        val out = arrayOfNulls<List<Pt>>(shares.size)
        partition(poly, shares.indices.sortedByDescending { shares[it] }, shares, out)
        return out.map { it ?: emptyList() }
    }

    private fun partition(region: List<Pt>, people: List<Int>, shares: List<Double>, out: Array<List<Pt>?>) {
        if (people.size == 1) {
            out[people[0]] = region
            return
        }
        // Largest first into whichever group is lighter.
        val a = ArrayList<Int>()
        val b = ArrayList<Int>()
        var wa = 0.0
        var wb = 0.0
        for (p in people) {
            if (wa <= wb) {
                a += p
                wa += shares[p]
            } else {
                b += p
                wb += shares[p]
            }
        }
        val xs = region.map { it.x }
        val ys = region.map { it.y }
        val alongX = (xs.max() - xs.min()) >= (ys.max() - ys.min())
        val nx = if (alongX) 1.0 else 0.0
        val ny = if (alongX) 0.0 else 1.0
        val cut = cutAt(region, nx, ny, wa / (wa + wb) * area(region))
        partition(clip(region, nx, ny, cut), a, shares, out)
        partition(clip(region, -nx, -ny, -cut), b, shares, out)
    }

    /** The c for which the part of [poly] with nx·x + ny·y ≤ c has area [target]. */
    private fun cutAt(poly: List<Pt>, nx: Double, ny: Double, target: Double): Double {
        var lo = poly.minOf { nx * it.x + ny * it.y }
        var hi = poly.maxOf { nx * it.x + ny * it.y }
        repeat(60) {
            val mid = (lo + hi) / 2
            if (area(clip(poly, nx, ny, mid)) < target) lo = mid else hi = mid
        }
        return (lo + hi) / 2
    }

    // Shape of an outline

    fun convexHull(points: List<Pt>): List<Pt> {
        val pts = points.distinct().sortedWith(compareBy({ it.x }, { it.y }))
        if (pts.size < 3) return pts
        fun cross(o: Pt, a: Pt, b: Pt) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        val lower = ArrayList<Pt>()
        for (p in pts) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0) lower.removeAt(lower.lastIndex)
            lower += p
        }
        val upper = ArrayList<Pt>()
        for (p in pts.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0) upper.removeAt(upper.lastIndex)
            upper += p
        }
        return lower.dropLast(1) + upper.dropLast(1)
    }

    /** Smallest rectangle around [poly], trying every hull edge as a side; angle in (-π/2, π/2]. */
    fun minAreaRect(poly: List<Pt>): OrientedRect {
        val hull = convexHull(poly)
        var best: OrientedRect? = null
        for (i in hull.indices) {
            val p = hull[i]
            val q = hull[(i + 1) % hull.size]
            var angle = atan2(q.y - p.y, q.x - p.x)
            while (angle > PI / 2) angle -= PI
            while (angle <= -PI / 2) angle += PI
            val c = cos(angle)
            val s = sin(angle)
            var u0 = Double.MAX_VALUE
            var u1 = -Double.MAX_VALUE
            var v0 = Double.MAX_VALUE
            var v1 = -Double.MAX_VALUE
            for (h in hull) {
                val u = h.x * c + h.y * s
                val v = -h.x * s + h.y * c
                u0 = min(u0, u); u1 = max(u1, u)
                v0 = min(v0, v); v1 = max(v1, v)
            }
            val um = (u0 + u1) / 2
            val vm = (v0 + v1) / 2
            val rect = OrientedRect(um * c - vm * s, um * s + vm * c, u1 - u0, v1 - v0, angle)
            if (best == null || rect.area < best.area) best = rect
        }
        return best ?: OrientedRect(0.0, 0.0, 0.0, 0.0, 0.0)
    }

    /** Outline area over its smallest rectangle: π/4 for an oval, 1 for a tray. */
    fun rectangularity(poly: List<Pt>): Double {
        val rect = minAreaRect(poly)
        return if (rect.area <= 0) 0.0 else area(poly) / rect.area
    }

    /** Douglas–Peucker on a closed outline: drops points within [epsilon] of the line kept. */
    fun simplify(poly: List<Pt>, epsilon: Double): List<Pt> {
        if (poly.size < 4) return poly
        // Split the ring at its two farthest-apart points and simplify each half.
        val a = 0
        val b = poly.indices.maxBy { hypot(poly[it].x - poly[a].x, poly[it].y - poly[a].y) }
        val first = dp(poly.subList(a, b + 1), epsilon)
        val second = dp(poly.subList(b, poly.size) + poly[a], epsilon)
        return first.dropLast(1) + second.dropLast(1)
    }

    private fun dp(line: List<Pt>, epsilon: Double): List<Pt> {
        if (line.size < 3) return line
        val p = line.first()
        val q = line.last()
        val len = hypot(q.x - p.x, q.y - p.y)
        var index = -1
        var far = 0.0
        for (i in 1 until line.lastIndex) {
            val m = line[i]
            val d = if (len < 1e-12) hypot(m.x - p.x, m.y - p.y)
            else abs((q.x - p.x) * (p.y - m.y) - (p.x - m.x) * (q.y - p.y)) / len
            if (d > far) {
                far = d
                index = i
            }
        }
        if (far <= epsilon) return listOf(p, q)
        return dp(line.subList(0, index + 1), epsilon).dropLast(1) + dp(line.subList(index, line.size), epsilon)
    }

    /** One pass of Chaikin corner cutting: softens the pixel steps a traced outline has. */
    fun smooth(poly: List<Pt>): List<Pt> {
        if (poly.size < 3) return poly
        val out = ArrayList<Pt>(poly.size * 2)
        for (i in poly.indices) {
            val p = poly[i]
            val q = poly[(i + 1) % poly.size]
            out += Pt(0.75 * p.x + 0.25 * q.x, 0.75 * p.y + 0.25 * q.y)
            out += Pt(0.25 * p.x + 0.75 * q.x, 0.25 * p.y + 0.75 * q.y)
        }
        return out
    }
}
