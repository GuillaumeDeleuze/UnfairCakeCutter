package com.unfaircake.cutter.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Turns a segmentation mask (true = cake) and the point the user tapped into a cake outline.
 * Pure Kotlin so it runs in plain JVM tests; the segmentation itself happens elsewhere.
 *
 * Steps: keep the piece under the finger, fill the holes decorations leave, then compare the
 * oval and the rectangle that share the piece's centre and second moments. Whichever overlaps
 * the piece better wins; if neither overlaps it well, it wasn't a cake.
 */
object SnapFit {

    /**
     * An outline in mask pixels. [width] runs along the rotated x axis; [rotationDeg] is in
     * [-45, 45] so the outline never turns more than it has to.
     */
    data class Result(
        val round: Boolean,
        val cx: Double,
        val cy: Double,
        val width: Double,
        val height: Double,
        val rotationDeg: Double,
    ) {
        fun scaled(f: Double) = copy(cx = cx * f, cy = cy * f, width = width * f, height = height * f)
    }

    /** Pieces smaller or larger than this share of the picture are not cakes. */
    private const val MIN_AREA = 0.01
    private const val MAX_AREA = 0.92

    /** Overlap (intersection over union) below which the piece has no recognisable shape. */
    private const val MIN_IOU = 0.72

    /** Ovals closer to a circle than this are left unturned. */
    private const val CIRCLE_RATIO = 1.08

    /** Rectangles at least this many times longer than wide are logs. */
    private const val LOG_RATIO = 2.4

    /**
     * A piece this close to an edge (share of the short side) touches it. A close-up cake can
     * run off one edge, or two opposite ones when it fills the width; the table around it
     * reaches a corner (two edges that meet) or more.
     */
    private const val EDGE_TOUCH = 0.04

    /** How far from the tap (share of the short side) a piece may start and still count. */
    private const val TAP_REACH = 0.06

    fun fromMask(mask: BooleanArray, width: Int, height: Int, tapX: Int, tapY: Int): Result? {
        require(mask.size == width * height)
        val start = nearestSet(mask, width, height, tapX, tapY) ?: return null
        val piece = fillHoles(component(mask, width, height, start), width, height)

        val area = piece.count { it }
        val total = width * height
        if (area < MIN_AREA * total || area > MAX_AREA * total) return null
        if (looksLikeBackground(piece, width, height)) return null

        // Centre and second moments of the piece.
        var sx = 0.0
        var sy = 0.0
        for (i in piece.indices) if (piece[i]) {
            sx += i % width + 0.5
            sy += i / width + 0.5
        }
        val mx = sx / area
        val my = sy / area
        var cxx = 0.0
        var cyy = 0.0
        var cxy = 0.0
        for (i in piece.indices) if (piece[i]) {
            val dx = i % width + 0.5 - mx
            val dy = i / width + 0.5 - my
            cxx += dx * dx
            cyy += dy * dy
            cxy += dx * dy
        }
        cxx /= area
        cyy /= area
        cxy /= area

        // Principal axes: eigenvalues of the covariance, angle of the major one.
        val tr = cxx + cyy
        val det = cxx * cyy - cxy * cxy
        val disc = sqrt(max(0.0, tr * tr / 4 - det))
        val major = tr / 2 + disc
        val minor = max(tr / 2 - disc, 1e-9)
        val theta = 0.5 * atan2(2 * cxy, cxx - cyy)

        // An oval with variance l along an axis has semi-axis 2√l; a rectangle, half side √(3l).
        val oval = Model(round = true, mx, my, 2 * sqrt(major), 2 * sqrt(minor), theta)
        val tray = Model(round = false, mx, my, sqrt(3 * major), sqrt(3 * minor), theta)
        val ovalIou = iou(piece, width, height, oval)
        val trayIou = iou(piece, width, height, tray)
        val best = if (ovalIou >= trayIou) oval else tray
        if (max(ovalIou, trayIou) < MIN_IOU) return null
        return best.toResult()
    }

    /** Which cake the outline is, keeping the user's tray layout when it still fits. */
    fun shapeFor(result: Result, current: CakeShape): CakeShape = when {
        result.round -> CakeShape.ROUND
        max(result.width, result.height) >= LOG_RATIO * min(result.width, result.height) -> CakeShape.LOG
        current == CakeShape.TRAY_STRIPS || current == CakeShape.TRAY_GRID -> current
        else -> CakeShape.TRAY_GRID
    }

    /** [result] in the overlay's pixels, as the fractions [ShapeTransform] stores. */
    fun toTransform(result: Result, canvasWidth: Float, canvasHeight: Float): ShapeTransform {
        val unit = min(canvasWidth, canvasHeight).toDouble()
        return ShapeTransform(
            cx = (result.cx / canvasWidth).toFloat(),
            cy = (result.cy / canvasHeight).toFloat(),
            width = (result.width / unit).toFloat(),
            height = (result.height / unit).toFloat(),
            rotationDeg = result.rotationDeg.toFloat(),
        ).clamped()
    }

    private class Model(
        val round: Boolean,
        val cx: Double,
        val cy: Double,
        val a: Double,
        val b: Double,
        val theta: Double,
    ) {
        private val c = cos(theta)
        private val s = sin(theta)

        fun contains(x: Double, y: Double): Boolean {
            val dx = x - cx
            val dy = y - cy
            val u = dx * c + dy * s
            val v = -dx * s + dy * c
            return if (round) (u / a) * (u / a) + (v / b) * (v / b) <= 1.0 else abs(u) <= a && abs(v) <= b
        }

        /** Half extent of the model's bounding box, for the overlap scan. */
        val reach: Double get() = if (round) a else sqrt(a * a + b * b)

        fun toResult(): Result {
            var w = 2 * a
            var h = 2 * b
            var deg = theta * 180.0 / PI
            // Keep the turn within ±45°: past that, swap the sides instead.
            if (deg > 45.0) {
                deg -= 90.0
                w = h.also { h = w }
            } else if (deg < -45.0) {
                deg += 90.0
                w = h.also { h = w }
            }
            if (round && max(w, h) < CIRCLE_RATIO * min(w, h)) deg = 0.0
            return Result(round, cx, cy, w, h, deg)
        }
    }

    private fun iou(piece: BooleanArray, width: Int, height: Int, model: Model): Double {
        var inter = 0
        var union = 0
        val r = model.reach
        val x0 = min(0, (model.cx - r).toInt())
        val x1 = max(width - 1, (model.cx + r).roundToInt())
        val y0 = min(0, (model.cy - r).toInt())
        val y1 = max(height - 1, (model.cy + r).roundToInt())
        for (y in y0..y1) for (x in x0..x1) {
            val inPiece = x in 0 until width && y in 0 until height && piece[y * width + x]
            val inModel = model.contains(x + 0.5, y + 0.5)
            if (inPiece && inModel) inter++
            if (inPiece || inModel) union++
        }
        return if (union == 0) 0.0 else inter.toDouble() / union
    }

    private fun looksLikeBackground(piece: BooleanArray, width: Int, height: Int): Boolean {
        val m = max(1, (EDGE_TOUCH * min(width, height)).roundToInt())
        var left = false
        var right = false
        var top = false
        var bottom = false
        for (i in piece.indices) if (piece[i]) {
            val x = i % width
            val y = i / width
            if (x < m) left = true
            if (x >= width - m) right = true
            if (y < m) top = true
            if (y >= height - m) bottom = true
        }
        return (left || right) && (top || bottom)
    }

    /** The set pixel under the tap, or the closest one within reach. */
    private fun nearestSet(mask: BooleanArray, width: Int, height: Int, tapX: Int, tapY: Int): Int? {
        val tx = tapX.coerceIn(0, width - 1)
        val ty = tapY.coerceIn(0, height - 1)
        if (mask[ty * width + tx]) return ty * width + tx
        val reach = max(1, (TAP_REACH * min(width, height)).roundToInt())
        var best: Int? = null
        var bestD = Int.MAX_VALUE
        for (y in max(0, ty - reach)..min(height - 1, ty + reach)) {
            for (x in max(0, tx - reach)..min(width - 1, tx + reach)) {
                if (!mask[y * width + x]) continue
                val d = (x - tx) * (x - tx) + (y - ty) * (y - ty)
                if (d <= reach * reach && d < bestD) {
                    bestD = d
                    best = y * width + x
                }
            }
        }
        return best
    }

    /** 4-connected piece of [mask] containing [start]. */
    private fun component(mask: BooleanArray, width: Int, height: Int, start: Int): BooleanArray {
        val out = BooleanArray(mask.size)
        val stack = IntArray(mask.size)
        var top = 0
        stack[top++] = start
        out[start] = true
        while (top > 0) {
            val i = stack[--top]
            val x = i % width
            val y = i / width
            fun visit(j: Int) {
                if (mask[j] && !out[j]) {
                    out[j] = true
                    stack[top++] = j
                }
            }
            if (x > 0) visit(i - 1)
            if (x < width - 1) visit(i + 1)
            if (y > 0) visit(i - width)
            if (y < height - 1) visit(i + width)
        }
        return out
    }

    /** Everything the outside can't reach without crossing [piece] belongs to it. */
    private fun fillHoles(piece: BooleanArray, width: Int, height: Int): BooleanArray {
        val outside = BooleanArray(piece.size)
        val stack = IntArray(piece.size)
        var top = 0
        fun seed(i: Int) {
            if (!piece[i] && !outside[i]) {
                outside[i] = true
                stack[top++] = i
            }
        }
        for (x in 0 until width) {
            seed(x)
            seed((height - 1) * width + x)
        }
        for (y in 0 until height) {
            seed(y * width)
            seed(y * width + width - 1)
        }
        while (top > 0) {
            val i = stack[--top]
            val x = i % width
            val y = i / width
            if (x > 0) seed(i - 1)
            if (x < width - 1) seed(i + 1)
            if (y > 0) seed(i - width)
            if (y < height - 1) seed(i + width)
        }
        return BooleanArray(piece.size) { !outside[it] }
    }
}
