package com.unfaircake.cutter.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.unfaircake.cutter.domain.CakeGeometry
import com.unfaircake.cutter.domain.CakeShape
import com.unfaircake.cutter.domain.Freeform
import com.unfaircake.cutter.domain.Pt
import com.unfaircake.cutter.domain.MAX_SHAPE_SIZE
import com.unfaircake.cutter.domain.MIN_SHAPE_SIZE
import com.unfaircake.cutter.domain.RectD
import com.unfaircake.cutter.domain.ShapeFrame
import com.unfaircake.cutter.domain.ShapeTransform
import com.unfaircake.cutter.domain.Vec2
import com.unfaircake.cutter.domain.Wedge
import com.unfaircake.cutter.ui.theme.Bricolage
import com.unfaircake.cutter.ui.theme.Candy
import com.unfaircake.cutter.ui.theme.sliceColor
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

private const val SLICE_ALPHA = 0.4f
private const val OUTSIDE_DIM = 0.58f

/**
 * Cutting guides drawn over the camera or photo.
 *
 * Gestures: a finger on a corner handle stretches width and height (the opposite corner stays
 * put); one finger inside the cake drags it; two fingers pinch to resize and twist to rotate.
 * Every change is reported through [onTransformChange] so the sliders stay in sync.
 */
@Composable
fun CakeOverlay(
    shape: CakeShape,
    transform: ShapeTransform,
    shares: List<Double>,
    percentLabels: List<String>,
    description: String,
    onTransformChange: (ShapeTransform) -> Unit,
    modifier: Modifier = Modifier,
    snapping: Boolean = false,
    dimGuides: Boolean = snapping,
    onSnapTap: (Offset, IntSize) -> Unit = { _, _ -> },
    snapToken: Int = 0,
    freeform: List<Pt>? = null,
    onHold: (Offset, IntSize) -> Unit = { _, _ -> },
    onHoldMove: (Offset) -> Unit = {},
    onHoldEnd: () -> Unit = {},
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val metrics = remember(density) { overlayMetrics(density) }
    val labelStyle = LabelStyle

    val currentShape by rememberUpdatedState(shape)
    val currentFreeform by rememberUpdatedState(freeform)
    val currentTransform by rememberUpdatedState(transform)
    val currentOnChange by rememberUpdatedState(onTransformChange)
    val currentSnapping by rememberUpdatedState(snapping)
    val currentOnSnapTap by rememberUpdatedState(onSnapTap)
    val currentOnHold by rememberUpdatedState(onHold)
    val currentOnHoldMove by rememberUpdatedState(onHoldMove)
    val currentOnHoldEnd by rememberUpdatedState(onHoldEnd)

    val gestures = Modifier.pointerInput(metrics) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (currentSnapping) {
                // Snap mode: a tap says where the cake is; nothing moves.
                down.consume()
                val up = waitForUpOrCancellation()
                if (up != null && (up.position - down.position).getDistance() < viewConfiguration.touchSlop) {
                    up.consume()
                    currentOnSnapTap(up.position, size)
                }
                return@awaitEachGesture
            }
            val w = size.width.toFloat()
            val h = size.height.toFloat()
            if (w <= 0f || h <= 0f) return@awaitEachGesture
            val unit = minOf(w, h)
            val minSide = max(metrics.minSide, MIN_SHAPE_SIZE * unit)
            val maxSide = MAX_SHAPE_SIZE * unit
            // Work on a local copy during the gesture: the state round-trip lags a frame behind.
            var frame = currentTransform.toFrame(w, h)
            val downPos = down.position.toVec()
            val corner = frame.hitCorner(downPos, metrics.handleHitRadius)

            // A finger that stays put is a live snap on whatever is under it; one that moves,
            // or a second finger, is a drag, a pinch or a stretch as before.
            var outcome = Press.Hold
            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) {
                        outcome = Press.Tap
                        break
                    }
                    val slid = (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                    if (slid || event.changes.count { it.pressed } > 1) {
                        outcome = Press.Move
                        break
                    }
                }
            }
            if (outcome == Press.Tap) return@awaitEachGesture
            if (outcome == Press.Hold) {
                down.consume()
                currentOnHold(down.position, size)
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        // The finger can slide onto the cake while held.
                        if (change.positionChanged()) currentOnHoldMove(change.position)
                        change.consume()
                    }
                } finally {
                    currentOnHoldEnd()
                }
                return@awaitEachGesture
            }

            when {
                corner != null -> {
                    down.consume()
                    val opposite = frame.corners()[(corner + 2) % 4]
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (change.positionChanged()) {
                            // Keep the unclamped frame so the opposite corner stays pinned.
                            frame = frame.resizedFromCorner(opposite, change.position.toVec(), minSide)
                            currentOnChange(frame.toTransform(w, h))
                            change.consume()
                        }
                    }
                }

                // A snapped outline is grabbed anywhere in its box.
                frame.contains(downPos, currentShape.isRound && currentFreeform == null) -> {
                    down.consume()
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val rotation = event.calculateRotation()
                        val pan = event.calculatePan()
                        val centroid = event.calculateCentroid(useCurrent = true)
                        if (centroid.isSpecified && (zoom != 1f || rotation != 0f || pan != Offset.Zero)) {
                            frame = frame.transformed(pan.toVec(), zoom, rotation, centroid.toVec(), minSide, maxSide)
                            val t = frame.toTransform(w, h)
                            frame = t.toFrame(w, h)
                            currentOnChange(t)
                        }
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    } while (event.changes.any { it.pressed })
                }

                // Outside the cake: leave the touch alone.
                else -> Unit
            }
        }
    }

    val shown = animatedShares(shares)
    val shownTransform = animatedTransform(transform, snapToken)
    // While aiming a snap the guides fade back so the whole cake shows.
    val guideAlpha by animateFloatAsState(if (dimGuides) 0.3f else 1f, tween(200), label = "guides")
    Canvas(
        modifier
            .then(gestures)
            .graphicsLayer { alpha = guideAlpha }
            .semantics { contentDescription = description },
    ) {
        val frame = shownTransform.toFrame(size.width, size.height)
        drawCake(frame, shape, shown, percentLabels, textMeasurer, labelStyle, metrics, freeform)
    }
}

/**
 * The outline as drawn: follows gestures exactly, but glides over on a spring when a snap
 * ([token] changed) moves it somewhere new.
 */
@Composable
private fun animatedTransform(target: ShapeTransform, token: Int): ShapeTransform {
    val cx = remember { Animatable(target.cx) }
    val cy = remember { Animatable(target.cy) }
    val w = remember { Animatable(target.width) }
    val h = remember { Animatable(target.height) }
    val rot = remember { Animatable(target.rotationDeg) }
    var lastToken by remember { mutableIntStateOf(token) }
    LaunchedEffect(target, token) {
        if (token != lastToken) {
            lastToken = token
            val spec = spring<Float>(dampingRatio = 0.6f, stiffness = 200f)
            // Turn the short way round.
            val turn = ShapeTransform.normalizeDegrees(target.rotationDeg - rot.value)
            coroutineScope {
                launch { cx.animateTo(target.cx, spec) }
                launch { cy.animateTo(target.cy, spec) }
                launch { w.animateTo(target.width, spec) }
                launch { h.animateTo(target.height, spec) }
                launch { rot.animateTo(rot.value + turn, spec) }
            }
            rot.snapTo(target.rotationDeg)
        } else {
            cx.snapTo(target.cx)
            cy.snapTo(target.cy)
            w.snapTo(target.width)
            h.snapTo(target.height)
            rot.snapTo(target.rotationDeg)
        }
    }
    return ShapeTransform(cx.value, cy.value, w.value, h.value, rot.value)
}

/**
 * The shares, easing towards new values on a spring so a reroll or an unfairness change visibly
 * slides the cuts around. Adding or removing a person jumps straight to the new cut.
 */
@Composable
private fun animatedShares(target: List<Double>): List<Double> {
    val values = remember(target.size) { target.map { Animatable(it.toFloat()) } }
    LaunchedEffect(values, target) {
        target.forEachIndexed { i, v ->
            launch { values[i].animateTo(v.toFloat(), spring(dampingRatio = 0.7f, stiffness = 170f)) }
        }
    }
    val current = values.map { it.value.toDouble().coerceAtLeast(0.0) }
    val sum = current.sum()
    return if (sum > 0.0) current.map { it / sum } else target
}

private val LabelStyle = TextStyle(
    color = Color.White,
    fontFamily = Bricolage,
    fontSize = 13.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 0.01.em,
)

private fun overlayMetrics(density: Density): OverlayMetrics = with(density) {
    OverlayMetrics(
        handleRadius = 8.dp.toPx(),
        handleRing = 3.dp.toPx(),
        handleStroke = 2.5.dp.toPx(),
        handleHitRadius = 32.dp.toPx(),
        cutWidth = 2.6.dp.toPx(),
        cutOutlineWidth = 5.5.dp.toPx(),
        leaderWidth = 1.6.dp.toPx(),
        leaderOutlineWidth = 3.5.dp.toPx(),
        labelPadH = 9.dp.toPx(),
        labelPadV = 3.dp.toPx(),
        labelBorder = 2.dp.toPx(),
        labelGap = 10.dp.toPx(),
        minSide = 40.dp.toPx(),
        dash = 6.dp.toPx(),
        boxWidth = 1.5.dp.toPx(),
    )
}

private class OverlayMetrics(
    val handleRadius: Float,
    val handleRing: Float,
    val handleStroke: Float,
    val handleHitRadius: Float,
    val cutWidth: Float,
    val cutOutlineWidth: Float,
    val leaderWidth: Float,
    val leaderOutlineWidth: Float,
    val labelPadH: Float,
    val labelPadV: Float,
    val labelBorder: Float,
    val labelGap: Float,
    val minSide: Float,
    val dash: Float,
    val boxWidth: Float,
)

private enum class Press { Tap, Hold, Move }

/** A label to draw upright at [anchor] (local frame), with an optional leader line. */
private class PieceLabel(val index: Int, val anchor: Offset, val leaderFrom: Offset? = null)

private fun DrawScope.drawCake(
    frame: ShapeFrame,
    shape: CakeShape,
    shares: List<Double>,
    percentLabels: List<String>,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    m: OverlayMetrics,
    freeform: List<Pt>?,
) {
    val hw = frame.width / 2f
    val hh = frame.height / 2f
    val bounds = Rect(-hw, -hh, hw, hh)
    // The snapped cake's own outline, in pixels of the local frame.
    val poly = freeform?.map { Pt(it.x * frame.width, it.y * frame.height) }
    val outline = Path().apply {
        when {
            poly != null -> addPolygon(poly)
            shape.isRound -> addOval(bounds)
            else -> addRect(bounds)
        }
    }

    val texts = shares.indices.map { i -> "${i + 1} · ${percentLabels.getOrElse(i) { "" }}" }
    val layouts = texts.map { textMeasurer.measure(AnnotatedString(it), style = labelStyle) }
    val labelSizes = layouts.map { Size(it.size.width.toFloat(), it.size.height.toFloat()) }

    val pieces: List<Path>
    val labels: List<PieceLabel>
    if (poly != null) {
        val cut = freeformPieces(poly, outline, shape, shares, frame, hw, hh, labelSizes, m)
        pieces = cut.first
        labels = cut.second
    } else when (shape) {
        CakeShape.ROUND -> {
            val wedges = CakeGeometry.wedges(shares)
            val single = wedges.size == 1
            pieces = if (single) listOf(outline) else wedges.map { wedgePath(it, hw, hh) }
            labels = wedges.mapIndexed { i, wedge ->
                val (x, y) = CakeGeometry.wedgeLabelAnchor(wedge, hw.toDouble(), hh.toDouble(), single)
                PieceLabel(i, Offset(x.toFloat(), y.toFloat()))
            }
        }

        CakeShape.TRAY_GRID -> {
            val rects = CakeGeometry.squarified(shares, frame.width.toDouble(), frame.height.toDouble())
            pieces = rects.map { rectPath(it) }
            labels = rects.mapIndexed { i, r -> PieceLabel(i, Offset(r.centerX.toFloat(), r.centerY.toFloat())) }
        }

        CakeShape.TRAY_STRIPS, CakeShape.LOG -> {
            val w = frame.width.toDouble()
            val h = frame.height.toDouble()
            val rects = CakeGeometry.strips(shares, w, h)
            val alongX = CakeGeometry.stripsAlongX(w, h)
            pieces = rects.map { rectPath(it) }
            labels = stripLabels(rects, alongX, hw, hh, labelSizes, m)
        }
    }

    withTransform({
        translate(frame.center.x, frame.center.y)
        rotate(frame.rotationDeg, pivot = Offset.Zero)
    }) {
        // Darken everything outside the cake. The rectangle is big enough to cover the whole
        // preview whatever the cake's position and angle.
        val reach = 2f * hypot(size.width, size.height) + frame.width + frame.height
        clipPath(outline, clipOp = ClipOp.Difference) {
            drawRect(Candy.Shade.copy(alpha = OUTSIDE_DIM), topLeft = Offset(-reach, -reach), size = Size(2 * reach, 2 * reach))
        }

        pieces.forEachIndexed { i, path -> drawPath(path, sliceColor(i).copy(alpha = SLICE_ALPHA)) }

        // White cut lines with a dark outline: all outlines first, so no white line is covered.
        val dark = Stroke(width = m.cutOutlineWidth, join = StrokeJoin.Round)
        val white = Stroke(width = m.cutWidth, join = StrokeJoin.Round)
        pieces.forEach { drawPath(it, Candy.Night.copy(alpha = 0.65f), style = dark) }
        pieces.forEach { drawPath(it, Color.White, style = white) }

        // Leader lines for labels moved outside thin slices.
        labels.forEach { label ->
            val from = label.leaderFrom ?: return@forEach
            drawLine(Candy.Night.copy(alpha = 0.6f), from, label.anchor, strokeWidth = m.leaderOutlineWidth)
            drawLine(Color.White, from, label.anchor, strokeWidth = m.leaderWidth)
        }

        // Bounding box (for ovals) and corner handles.
        if (shape.isRound || poly != null) {
            drawRect(
                Color.White.copy(alpha = 0.7f),
                topLeft = Offset(-hw, -hh),
                size = Size(frame.width, frame.height),
                style = Stroke(width = m.boxWidth, pathEffect = PathEffect.dashPathEffect(floatArrayOf(m.dash, m.dash))),
            )
        }
        listOf(Offset(-hw, -hh), Offset(hw, -hh), Offset(hw, hh), Offset(-hw, hh)).forEach { c ->
            drawCircle(Candy.Ink, radius = m.handleRadius + m.handleRing, center = c)
            drawCircle(Candy.Pink, radius = m.handleRadius, center = c)
            drawCircle(Color.White, radius = m.handleRadius, center = c, style = Stroke(width = m.handleStroke))
        }
    }

    // Labels are drawn upright in screen space so they stay readable at any rotation.
    labels.forEach { label ->
        val layout = layouts[label.index]
        val world = frame.toWorld(label.anchor.toVec())
        val textW = layout.size.width.toFloat()
        val textH = layout.size.height.toFloat()
        // CSS-style box: the border sits inside the pill, around the padding.
        val inset = m.labelPadH + m.labelBorder
        val pill = Size(textW + 2 * inset, textH + 2 * (m.labelPadV + m.labelBorder))
        val pillTopLeft = Offset(world.x - pill.width / 2f, world.y - pill.height / 2f)
        val radius = CornerRadius(pill.height / 2f)
        drawRoundRect(Candy.Night.copy(alpha = 0.78f), topLeft = pillTopLeft, size = pill, cornerRadius = radius)
        val half = m.labelBorder / 2f
        drawRoundRect(
            sliceColor(label.index),
            topLeft = pillTopLeft + Offset(half, half),
            size = Size(pill.width - m.labelBorder, pill.height - m.labelBorder),
            cornerRadius = CornerRadius(pill.height / 2f - half),
            style = Stroke(width = m.labelBorder),
        )
        drawText(layout, topLeft = Offset(world.x - textW / 2f, world.y - textH / 2f))
    }
}

/**
 * Labels for strips and logs. When any slice is too thin for its label, labels move outside the
 * cake and alternate above and below (left and right for an upright log), each with a leader.
 */
private fun stripLabels(
    rects: List<RectD>,
    alongX: Boolean,
    hw: Float,
    hh: Float,
    labelSizes: List<Size>,
    m: OverlayMetrics,
): List<PieceLabel> {
    val thin = rects.indices.any { i ->
        val thickness = if (alongX) rects[i].width else rects[i].height
        val needed = (if (alongX) labelSizes[i].width else labelSizes[i].height) + 2 * m.labelPadH + m.labelGap
        thickness < needed
    }
    return rects.mapIndexed { i, r ->
        val cx = r.centerX.toFloat()
        val cy = r.centerY.toFloat()
        if (!thin || rects.size == 1) {
            PieceLabel(i, Offset(cx, cy))
        } else {
            val side = if (i % 2 == 0) -1f else 1f
            val size = labelSizes[i]
            if (alongX) {
                val edge = Offset(cx, side * hh)
                // Every other label sits a row further out, so neighbours never touch.
                val step = size.height + 2 * m.labelPadV
                val row = (i / 2) % 2
                val dist = hh + m.labelGap + step / 2f + row * (step + m.labelGap / 2f)
                PieceLabel(i, Offset(cx, side * dist), leaderFrom = edge)
            } else {
                val edge = Offset(side * hw, cy)
                val dist = hw + m.labelGap + size.width / 2f + m.labelPadH
                PieceLabel(i, Offset(side * dist, cy), leaderFrom = edge)
            }
        }
    }
}

/** Pieces and labels for a snapped outline, cut by area share within its real shape. */
private fun freeformPieces(
    poly: List<Pt>,
    outline: Path,
    shape: CakeShape,
    shares: List<Double>,
    frame: ShapeFrame,
    hw: Float,
    hh: Float,
    labelSizes: List<Size>,
    m: OverlayMetrics,
): Pair<List<Path>, List<PieceLabel>> = when (shape) {
    CakeShape.ROUND -> {
        val c = Freeform.centroid(poly)
        val wedges = Freeform.wedges(poly, shares)
        if (wedges.size <= 1) {
            listOf(outline) to wedges.indices.map { PieceLabel(it, Offset(c.x.toFloat(), c.y.toFloat())) }
        } else {
            // A fan wide enough to reach past the outline, trimmed to it.
            val far = 2f * (frame.width + frame.height)
            val paths = wedges.map { w -> Path.combine(PathOperation.Intersect, outline, fanPath(w, c, far)) }
            val labels = wedges.mapIndexed { i, w ->
                val r = Freeform.reach(poly, c, w.mid) * if (w.sweep < PI / 6) 0.72 else 0.6
                PieceLabel(i, Offset((c.x + r * cos(w.mid)).toFloat(), (c.y + r * sin(w.mid)).toFloat()))
            }
            paths to labels
        }
    }

    CakeShape.TRAY_GRID -> {
        val parts = Freeform.grid(poly, shares)
        parts.map { polygonPath(it) } to parts.mapIndexed { i, p ->
            val c = Freeform.centroid(p)
            PieceLabel(i, Offset(c.x.toFloat(), c.y.toFloat()))
        }
    }

    CakeShape.TRAY_STRIPS, CakeShape.LOG -> {
        val alongX = CakeGeometry.stripsAlongX(frame.width.toDouble(), frame.height.toDouble())
        val parts = Freeform.strips(poly, shares, alongX)
        // Strip labels only need each strip's extent along the cut direction.
        val rects = parts.map { p ->
            if (p.isEmpty()) {
                RectD(0.0, 0.0, 0.0, 0.0)
            } else {
                val x0 = p.minOf { it.x }
                val y0 = p.minOf { it.y }
                RectD(x0, y0, p.maxOf { it.x } - x0, p.maxOf { it.y } - y0)
            }
        }
        parts.map { polygonPath(it) } to stripLabels(rects, alongX, hw, hh, labelSizes, m)
    }
}

private fun Path.addPolygon(poly: List<Pt>) {
    if (poly.isEmpty()) return
    moveTo(poly[0].x.toFloat(), poly[0].y.toFloat())
    for (i in 1 until poly.size) lineTo(poly[i].x.toFloat(), poly[i].y.toFloat())
    close()
}

private fun polygonPath(poly: List<Pt>): Path = Path().apply { addPolygon(poly) }

/** A wedge of radius [far] around [c], for trimming to the cake's outline. */
private fun fanPath(wedge: Wedge, c: Pt, far: Float): Path = Path().apply {
    moveTo(c.x.toFloat(), c.y.toFloat())
    val steps = max(2, ceil(wedge.sweep / (PI / 90.0)).toInt())
    for (s in 0..steps) {
        val t = wedge.start + wedge.sweep * s / steps
        lineTo((c.x + far * cos(t)).toFloat(), (c.y + far * sin(t)).toFloat())
    }
    close()
}

private fun wedgePath(wedge: Wedge, a: Float, b: Float): Path = Path().apply {
    moveTo(0f, 0f)
    // About one segment every 2°, enough for a smooth rim at any size.
    val steps = max(2, ceil(wedge.sweep / (PI / 90.0)).toInt())
    for (s in 0..steps) {
        val t = wedge.start + wedge.sweep * s / steps
        lineTo((a * cos(t)).toFloat(), (b * sin(t)).toFloat())
    }
    close()
}

private fun rectPath(r: RectD): Path = Path().apply {
    addRect(Rect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()))
}

private fun Offset.toVec() = Vec2(x, y)
