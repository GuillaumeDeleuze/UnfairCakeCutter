package com.unfaircake.cutter.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** What kind of cake is on the table. */
enum class CakeShape {
    /** Round or oval cake: wedges from the centre. */
    ROUND,

    /** Tray cut in parallel strips. */
    TRAY_STRIPS,

    /** Tray cut in near-square pieces (squarified treemap). */
    TRAY_GRID,

    /** Roll cake, bûche, terrine: slices across the length only. */
    LOG;

    val isRound: Boolean get() = this == ROUND

    /** Default size when switching to this shape, as fractions of the preview's short side. */
    val defaultWidth: Float
        get() = when (this) {
            ROUND -> 0.78f
            TRAY_STRIPS, TRAY_GRID -> 0.88f
            LOG -> 0.92f
        }

    val defaultHeight: Float
        get() = when (this) {
            // A round cake seen from across the table looks like an oval.
            ROUND -> 0.6f
            TRAY_STRIPS, TRAY_GRID -> 0.62f
            LOG -> 0.24f
        }
}

/** Axis-aligned rectangle in the cake's local frame (origin at the cake centre). */
data class RectD(val left: Double, val top: Double, val width: Double, val height: Double) {
    val right: Double get() = left + width
    val bottom: Double get() = top + height
    val centerX: Double get() = left + width / 2.0
    val centerY: Double get() = top + height / 2.0
    val area: Double get() = width * height
}

/** A wedge on the unit circle; angles in radians, 0 = +x, growing clockwise on screen (+y is down). */
data class Wedge(val start: Double, val sweep: Double) {
    val mid: Double get() = start + sweep / 2.0
}

/**
 * Pure slicing geometry. Every function takes shares in person order and returns one piece per
 * person in the same order, so piece i always belongs to person i.
 */
object CakeGeometry {

    /** Wedges start at 12 o'clock. */
    const val START_ANGLE: Double = -PI / 2.0

    /**
     * Wedges computed on the unit circle. The overlay then scales x by a and y by b to get an
     * ellipse; that affine map multiplies every area by the same factor (a·b), so the area of
     * each slice stays proportional to its angle, i.e. to its share.
     */
    fun wedges(shares: List<Double>): List<Wedge> {
        val total = shares.sum()
        if (shares.isEmpty() || total <= 0.0) return emptyList()
        var angle = START_ANGLE
        return shares.map { share ->
            val sweep = 2.0 * PI * share / total
            Wedge(angle, sweep).also { angle += sweep }
        }
    }

    /** Point on the ellipse with half-axes [a], [b] at unit-circle parameter [t]. */
    fun ellipsePoint(t: Double, a: Double, b: Double): Pair<Double, Double> = a * cos(t) to b * sin(t)

    /**
     * Area of the ellipse sector between parameters t0 and t0+sweep: a·b·sweep/2.
     * (Exposed so tests can check the proportionality claim.)
     */
    fun ellipseSectorArea(sweep: Double, a: Double, b: Double): Double = a * b * sweep / 2.0

    /** Label anchor for a wedge, in the ellipse's local frame. */
    fun wedgeLabelAnchor(wedge: Wedge, a: Double, b: Double, single: Boolean): Pair<Double, Double> {
        if (single) return 0.0 to 0.0
        // Thin wedges push the label further out where the wedge is wider.
        val r = if (wedge.sweep < PI / 6) 0.72 else 0.6
        return a * r * cos(wedge.mid) to b * r * sin(wedge.mid)
    }

    /**
     * Parallel slices across the long side of a [width]×[height] box centred on the origin:
     * each cut is parallel to the short side and each slice's thickness is proportional to its
     * share. Used for tray strips and for logs.
     */
    fun strips(shares: List<Double>, width: Double, height: Double): List<RectD> {
        val total = shares.sum()
        if (shares.isEmpty() || total <= 0.0) return emptyList()
        val alongX = width >= height
        val length = if (alongX) width else height
        var pos = -length / 2.0
        return shares.mapIndexed { i, share ->
            // The last slice ends exactly on the edge, whatever the rounding did.
            val thickness = if (i == shares.lastIndex) length / 2.0 - pos else length * share / total
            val rect = if (alongX) {
                RectD(pos, -height / 2.0, thickness, height)
            } else {
                RectD(-width / 2.0, pos, width, thickness)
            }
            pos += thickness
            rect
        }
    }

    /** True when the log is sliced along its x axis (it is at least as wide as tall). */
    fun stripsAlongX(width: Double, height: Double): Boolean = width >= height

    /**
     * Squarified treemap (Bruls, Huizing & van Wijk) of a [width]×[height] box centred on the
     * origin. Each piece's area equals share × box area, and rows are grown only while that
     * keeps the worst aspect ratio from getting worse, so pieces stay close to square.
     */
    fun squarified(shares: List<Double>, width: Double, height: Double): List<RectD> {
        val total = shares.sum()
        if (shares.isEmpty() || total <= 0.0) return emptyList()
        val boxArea = width * height
        val order = shares.indices.sortedByDescending { shares[it] }
        val areas = order.map { it to shares[it] / total * boxArea }
        val result = arrayOfNulls<RectD>(shares.size)

        var free = RectD(-width / 2.0, -height / 2.0, width, height)
        val row = mutableListOf<Pair<Int, Double>>()
        var i = 0
        while (i < areas.size) {
            val side = min(free.width, free.height)
            val candidate = areas[i]
            if (row.isEmpty() || worstRatio(row + candidate, side) <= worstRatio(row, side)) {
                row += candidate
                i++
            } else {
                free = layoutRow(row, free, result, isLast = false)
                row.clear()
            }
        }
        if (row.isNotEmpty()) layoutRow(row, free, result, isLast = true)
        return result.map { requireNotNull(it) }
    }

    /** Worst (largest) aspect ratio in a row laid along a side of length [side]. */
    private fun worstRatio(row: List<Pair<Int, Double>>, side: Double): Double {
        val s = row.sumOf { it.second }
        if (s <= 0.0 || side <= 0.0) return Double.POSITIVE_INFINITY
        val side2 = side * side
        return row.maxOf { (_, a) -> max(side2 * a / (s * s), (s * s) / (side2 * a)) }
    }

    /** Places [row] along the short side of [free] and returns the space that is left. */
    private fun layoutRow(
        row: List<Pair<Int, Double>>,
        free: RectD,
        out: Array<RectD?>,
        isLast: Boolean,
    ): RectD {
        val s = row.sumOf { it.second }
        return if (free.width >= free.height) {
            // Column on the left, pieces stacked top to bottom.
            val colWidth = if (isLast) free.width else s / free.height
            var y = free.top
            row.forEachIndexed { j, (index, a) ->
                val h = if (j == row.lastIndex) free.bottom - y else a / colWidth
                out[index] = RectD(free.left, y, colWidth, h)
                y += h
            }
            RectD(free.left + colWidth, free.top, free.width - colWidth, free.height)
        } else {
            // Row on top, pieces left to right.
            val rowHeight = if (isLast) free.height else s / free.width
            var x = free.left
            row.forEachIndexed { j, (index, a) ->
                val w = if (j == row.lastIndex) free.right - x else a / rowHeight
                out[index] = RectD(x, free.top, w, rowHeight)
                x += w
            }
            RectD(free.left, free.top + rowHeight, free.width, free.height - rowHeight)
        }
    }

    /** Aspect ratio ≥ 1 of a rectangle (1 = square). */
    fun aspect(rect: RectD): Double {
        val lo = min(rect.width, rect.height)
        return if (lo <= 0.0) Double.POSITIVE_INFINITY else max(rect.width, rect.height) / lo
    }
}
