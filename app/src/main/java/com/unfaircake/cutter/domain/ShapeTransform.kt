package com.unfaircake.cutter.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

/** Smallest / largest cake side, as a fraction of the preview's short side. */
const val MIN_SHAPE_SIZE = 0.08f
const val MAX_SHAPE_SIZE = 1.6f

/** Plain 2D vector so the maths stays free of Android and Compose types. */
data class Vec2(val x: Float, val y: Float) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(f: Float) = Vec2(x * f, y * f)

    /** Rotates by [radians] (clockwise on screen, since +y points down). */
    fun rotated(radians: Float): Vec2 {
        val c = cos(radians)
        val s = sin(radians)
        return Vec2(x * c - y * s, x * s + y * c)
    }

    companion object {
        val Zero = Vec2(0f, 0f)
    }
}

/**
 * Where the cake outline sits, independent of the preview's pixel size so it survives layout
 * changes and process death.
 *
 * @property cx centre x as a fraction of the preview width
 * @property cy centre y as a fraction of the preview height
 * @property width full width as a fraction of the preview's short side
 * @property height full height as a fraction of the preview's short side
 * @property rotationDeg rotation in degrees, in [-180, 180]
 */
data class ShapeTransform(
    val cx: Float = 0.5f,
    // A little below the middle: the app title sits over the top of the preview.
    val cy: Float = 0.56f,
    val width: Float = CakeShape.ROUND.defaultWidth,
    val height: Float = CakeShape.ROUND.defaultHeight,
    val rotationDeg: Float = 0f,
) {
    /** The "Size" slider value: the longer side. */
    val size: Float get() = max(width, height)

    fun withSize(newSize: Float): ShapeTransform {
        val target = newSize.coerceIn(MIN_SHAPE_SIZE, MAX_SHAPE_SIZE)
        val f = target / size
        return copy(width = width * f, height = height * f).clamped()
    }

    fun withRotation(degrees: Float): ShapeTransform = copy(rotationDeg = normalizeDegrees(degrees))

    fun clamped(): ShapeTransform = copy(
        cx = cx.coerceIn(0f, 1f),
        cy = cy.coerceIn(0f, 1f),
        width = width.coerceIn(MIN_SHAPE_SIZE, MAX_SHAPE_SIZE),
        height = height.coerceIn(MIN_SHAPE_SIZE, MAX_SHAPE_SIZE),
        rotationDeg = normalizeDegrees(rotationDeg),
    )

    fun toFrame(canvasWidth: Float, canvasHeight: Float): ShapeFrame {
        val unit = min(canvasWidth, canvasHeight)
        return ShapeFrame(
            center = Vec2(cx * canvasWidth, cy * canvasHeight),
            width = width * unit,
            height = height * unit,
            rotationDeg = rotationDeg,
        )
    }

    companion object {
        fun defaultFor(shape: CakeShape) = ShapeTransform(width = shape.defaultWidth, height = shape.defaultHeight)

        fun normalizeDegrees(degrees: Float): Float {
            var d = degrees % 360f
            if (d > 180f) d -= 360f
            if (d < -180f) d += 360f
            return d
        }
    }
}

/** The cake outline in preview pixels. Local frame: origin at the centre, x along the width. */
data class ShapeFrame(
    val center: Vec2,
    val width: Float,
    val height: Float,
    val rotationDeg: Float,
) {
    val rotationRad: Float get() = (rotationDeg * PI / 180.0).toFloat()

    fun toLocal(world: Vec2): Vec2 = (world - center).rotated(-rotationRad)
    fun toWorld(local: Vec2): Vec2 = local.rotated(rotationRad) + center

    /** Corners in world space, in order: top-left, top-right, bottom-right, bottom-left (local). */
    fun corners(): List<Vec2> {
        val hw = width / 2f
        val hh = height / 2f
        return listOf(Vec2(-hw, -hh), Vec2(hw, -hh), Vec2(hw, hh), Vec2(-hw, hh)).map(::toWorld)
    }

    fun contains(world: Vec2, round: Boolean): Boolean {
        val p = toLocal(world)
        val hw = width / 2f
        val hh = height / 2f
        if (hw <= 0f || hh <= 0f) return false
        return if (round) {
            val nx = p.x / hw
            val ny = p.y / hh
            nx * nx + ny * ny <= 1f
        } else {
            abs(p.x) <= hw && abs(p.y) <= hh
        }
    }

    /** Index of the corner handle within [radius] px of [world], or null. */
    fun hitCorner(world: Vec2, radius: Float): Int? {
        var best: Int? = null
        var bestDist = radius * radius
        corners().forEachIndexed { i, c ->
            val d = world - c
            val dist = d.x * d.x + d.y * d.y
            if (dist <= bestDist) {
                bestDist = dist
                best = i
            }
        }
        return best
    }

    /**
     * One step of a pan / pinch / twist gesture: rotates by [rotationDeltaDeg] and scales by [zoom]
     * around [centroid], then moves by [pan]. The zoom is limited so that both sides stay within
     * [minSide]..[maxSide] px and the aspect ratio is kept.
     */
    fun transformed(
        pan: Vec2,
        zoom: Float,
        rotationDeltaDeg: Float,
        centroid: Vec2,
        minSide: Float = 0f,
        maxSide: Float = Float.MAX_VALUE,
    ): ShapeFrame {
        val lo = if (min(width, height) > 0f) minSide / min(width, height) else 1f
        val hi = if (max(width, height) > 0f) maxSide / max(width, height) else 1f
        val z = if (lo <= hi) zoom.coerceIn(lo, hi) else 1f
        val rad = (rotationDeltaDeg * PI / 180.0).toFloat()
        val newCenter = centroid + (center - centroid).rotated(rad) * z + pan
        return copy(
            center = newCenter,
            width = width * z,
            height = height * z,
            rotationDeg = rotationDeg + rotationDeltaDeg,
        )
    }

    /**
     * Corner-handle drag: the [opposite] corner (world space) stays put and the dragged corner
     * follows [pointer]. Width and height change independently; rotation is kept.
     */
    fun resizedFromCorner(opposite: Vec2, pointer: Vec2, minSide: Float): ShapeFrame {
        val d = (pointer - opposite).rotated(-rotationRad)
        val newWidth = max(abs(d.x), minSide)
        val newHeight = max(abs(d.y), minSide)
        val sx = if (d.x == 0f) 1f else sign(d.x)
        val sy = if (d.y == 0f) 1f else sign(d.y)
        val halfDiagonal = Vec2(sx * newWidth / 2f, sy * newHeight / 2f).rotated(rotationRad)
        return copy(center = opposite + halfDiagonal, width = newWidth, height = newHeight)
    }

    fun toTransform(canvasWidth: Float, canvasHeight: Float): ShapeTransform {
        val unit = min(canvasWidth, canvasHeight)
        return ShapeTransform(
            cx = center.x / canvasWidth,
            cy = center.y / canvasHeight,
            width = width / unit,
            height = height / unit,
            rotationDeg = rotationDeg,
        ).clamped()
    }
}
