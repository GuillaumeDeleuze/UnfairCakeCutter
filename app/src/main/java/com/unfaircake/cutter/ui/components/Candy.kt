package com.unfaircake.cutter.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.unfaircake.cutter.ui.theme.Bricolage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.unfaircake.cutter.ui.theme.Candy

// Shared pieces of the "candy" look: thick ink outlines and flat, offset ink shadows.

val Pill = RoundedCornerShape(percent = 50)

/** A flat copy of [shape], offset down and right, drawn behind the element (never clipped by it). */
fun Modifier.hardShadow(offset: Dp, shape: Shape, color: Color = Candy.Ink): Modifier = drawBehind {
    val o = offset.toPx()
    if (o <= 0f) return@drawBehind
    val outline = shape.createOutline(size, layoutDirection, this)
    translate(o, o) { drawOutline(outline, color) }
}

/** A text shadow with no blur, the design's `text-shadow: Npx Npx 0 color`. */
@Composable
fun hardTextShadow(offset: Dp, color: Color): Shadow {
    val px = with(LocalDensity.current) { offset.toPx() }
    return remember(px, color) { Shadow(color = color, offset = Offset(px, px), blurRadius = 0f) }
}

/** Light "tick" from the device, no permission needed. */
@Composable
fun rememberTick(): () -> Unit {
    val view = LocalView.current
    return remember(view) { { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) } }
}

/**
 * 0 at rest, 1 fully pressed. Sinks fast, then springs back past 0 on release, so whatever uses
 * it overshoots a little before settling. A quick tap reports press and release almost together
 * (and late, inside a scrolling list), so the release waits for the sink to bottom out: every
 * tap plays the whole click.
 */
@Composable
fun pressProgress(interaction: MutableInteractionSource, enabled: Boolean = true): Float {
    val progress = remember { Animatable(0f) }
    val tick = rememberTick()
    LaunchedEffect(interaction, enabled) {
        if (!enabled) {
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        var sink: Job? = null
        interaction.interactions.collect { event ->
            when (event) {
                is PressInteraction.Press -> {
                    tick()
                    sink?.cancel()
                    sink = launch { progress.animateTo(1f, tween(durationMillis = 70)) }
                }

                is PressInteraction.Release -> {
                    val pending = sink
                    sink = launch {
                        pending?.join()
                        progress.animateTo(0f, spring(dampingRatio = 0.32f, stiffness = 520f))
                    }
                }

                // The finger turned into a scroll: no click, just settle.
                is PressInteraction.Cancel -> {
                    sink?.cancel()
                    sink = launch { progress.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 700f)) }
                }
            }
        }
    }
    return progress.value
}

/**
 * Button with an ink outline and a hard shadow. Pressed, it sinks onto its shadow and squashes a
 * touch; released, it springs back up with a small bounce.
 */
@Composable
fun CandyButton(
    onClick: () -> Unit,
    container: Color,
    modifier: Modifier = Modifier,
    contentColor: Color = Candy.Ink,
    shape: Shape = Pill,
    height: Dp = 48.dp,
    shadow: Dp = 4.dp,
    border: Dp = 3.dp,
    horizontalPadding: Dp = 20.dp,
    enabled: Boolean = true,
    fontSize: Int = 16,
    contentDescription: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val progress = pressProgress(interaction, enabled)
    val shadowPx = with(LocalDensity.current) { shadow.toPx() }
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .hardShadow(if (enabled) shadow else 0.dp, shape),
        // A fixed or full width given to the button reaches the face.
        propagateMinConstraints = true,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier = Modifier
                .graphicsLayer {
                    translationX = progress * shadowPx
                    translationY = progress * shadowPx
                    val squash = 1f - 0.06f * progress
                    scaleX = squash
                    scaleY = squash
                }
                .clip(shape)
                .background(container)
                .border(border, Candy.Ink, shape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .then(
                    if (contentDescription != null) {
                        Modifier.semantics { this.contentDescription = contentDescription }
                    } else {
                        Modifier
                    },
                )
                .height(height)
                .padding(horizontal = horizontalPadding),
        ) {
            CompositionLocalProvider(
                LocalTextStyle provides TextStyle(
                    fontFamily = Bricolage,
                    fontSize = fontSize.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = contentColor,
                ),
            ) { content() }
        }
    }
}

/** Round 44dp button used by the people stepper. */
@Composable
fun CandyCircleButton(
    onClick: () -> Unit,
    container: Color,
    contentDescription: String,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    CandyButton(
        onClick = onClick,
        container = container,
        shape = CircleShape,
        height = 44.dp,
        shadow = 3.dp,
        horizontalPadding = 0.dp,
        enabled = enabled,
        fontSize = 24,
        contentDescription = contentDescription,
        modifier = Modifier.size(44.dp),
    ) { content() }
}

/**
 * Text whose lines are exactly [TextStyle.lineHeight] tall, glyphs centred in each line and free
 * to overflow it, like CSS `line-height: 1`. Android never lets a line be shorter than the font's
 * own ascent + descent, which for Bagel is almost 1.5 em, so each line is laid out on its own.
 */
@Composable
fun CssLines(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    val lineHeight = style.lineHeight
    val single = style.copy(lineHeight = TextUnit.Unspecified)
    Column(modifier, horizontalAlignment = horizontalAlignment) {
        text.split('\n').forEach { line ->
            Text(
                text = line,
                style = single,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                    val height = if (lineHeight.isSpecified) lineHeight.roundToPx() else placeable.height
                    layout(placeable.width, height) { placeable.place(0, (height - placeable.height) / 2) }
                },
            )
        }
    }
}

/** One line of text that shrinks (down to [minFontSize]) rather than wrap when space runs out. */
@Composable
fun FitText(text: String, style: TextStyle, modifier: Modifier = Modifier, minFontSize: TextUnit = 11.sp) {
    BoxWithConstraints(modifier) {
        val measurer = rememberTextMeasurer()
        val room = constraints.maxWidth
        val size = remember(text, style, room) {
            var s = style.fontSize.value
            while (s > minFontSize.value &&
                measurer.measure(text, style.copy(fontSize = s.sp), maxLines = 1, softWrap = false).size.width > room
            ) {
                s -= 0.5f
            }
            s
        }
        Text(text, style = style.copy(fontSize = size.sp), maxLines = 1, softWrap = false)
    }
}

/** Small status pill: "● LIVE", "❚❚ FROZEN", "PHOTO". */
@Composable
fun CandyChip(text: String, container: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Candy.Ink,
        fontFamily = Bricolage,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.12.em,
        modifier = modifier
            .clip(Pill)
            .background(container)
            .border(2.5.dp, Candy.Ink, Pill)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Uppercase, tracked-out section title in magenta. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = Candy.Magenta,
        fontFamily = Bricolage,
        fontSize = 13.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.16.em,
        modifier = modifier,
    )
}

/** White pill holding the options; the pink selection slides over to the one picked. */
@Composable
fun <T> CandySegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val position by animateFloatAsState(
        targetValue = index.toFloat(),
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 420f),
        label = "segment",
    )
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(Pill)
            .background(Candy.White)
            .border(3.dp, Candy.Ink, Pill)
            .padding(4.dp),
    ) {
        val gap = 4.dp
        val itemWidth = (maxWidth - gap * (options.size - 1)) / options.size
        Box(
            Modifier
                .offset(x = (itemWidth + gap) * position)
                .width(itemWidth)
                .height(44.dp)
                .background(Candy.Pink, Pill),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.selectableGroup()) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                val interaction = remember { MutableInteractionSource() }
                val squash = 1f - 0.08f * pressProgress(interaction)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .graphicsLayer {
                            scaleX = squash
                            scaleY = squash
                        }
                        .clip(Pill)
                        .selectable(
                            selected = isSelected,
                            interactionSource = interaction,
                            indication = null,
                            role = Role.RadioButton,
                            onClick = { onSelect(value) },
                        ),
                ) {
                    Text(
                        text = label,
                        color = Candy.Ink,
                        fontFamily = Bricolage,
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

enum class SliderLook { Big, Small }

/**
 * Material slider (for its gestures and accessibility) dressed as the design: a fat outlined
 * track with a cream knob for unfairness, a thin ink track with a pink knob for size and angle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CandySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    look: SliderLook,
    modifier: Modifier = Modifier,
    steps: Int = 0,
) {
    val thumb = if (look == SliderLook.Big) 34.dp else 22.dp
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val dragged by interaction.collectIsDraggedAsState()
    val held = pressed || dragged
    val swell by animateFloatAsState(
        targetValue = if (held) 1.22f else 1f,
        animationSpec = spring(dampingRatio = 0.38f, stiffness = 600f),
        label = "knob",
    )
    val tick = rememberTick()
    LaunchedEffect(held) { if (held) tick() }
    Slider(
        value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        modifier = modifier,
        interactionSource = interaction,
        thumb = {
            if (look == SliderLook.Big) {
                Box(
                    Modifier
                        .size(thumb)
                        .graphicsLayer {
                            scaleX = swell
                            scaleY = swell
                        }
                        .hardShadow(3.dp, CircleShape)
                        .background(Candy.Cream, CircleShape)
                        .border(3.dp, Candy.Ink, CircleShape),
                )
            } else {
                Box(
                    Modifier
                        .size(thumb)
                        .graphicsLayer {
                            scaleX = swell
                            scaleY = swell
                        }
                        .background(Candy.Pink, CircleShape)
                        .border(3.dp, Candy.Ink, CircleShape),
                )
            }
        },
        track = { state -> CandyTrack(state, look, thumb) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CandyTrack(state: SliderState, look: SliderLook, thumb: Dp) {
    val range = state.valueRange
    val fraction = if (range.endInclusive > range.start) {
        ((state.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    } else {
        0f
    }
    val height = if (look == SliderLook.Big) 14.dp else 6.dp
    // Material lays the track out between the knob's extreme centres; the design's track runs
    // under the knobs to the full width, so it is drawn half a knob wider on each side.
    Canvas(Modifier.fillMaxWidth().height(height)) {
        val ext = thumb.toPx() / 2f
        val left = -ext
        val full = size.width + 2 * ext
        val knob = fraction * size.width
        if (look == SliderLook.Big) {
            val border = 3.dp.toPx()
            outlinedPill(Candy.Track, left, full, border)
            outlinedPill(Candy.Pink, left, knob - left + 2.dp.toPx(), border)
        } else {
            val r = CornerRadius(size.height / 2f)
            drawRoundRect(Candy.Track, Offset(left, 0f), Size(full, size.height), r)
            drawRoundRect(Candy.Ink, Offset(left, 0f), Size(knob - left, size.height), r)
        }
    }
}

private fun DrawScope.outlinedPill(fill: Color, left: Float, width: Float, border: Float) {
    val r = CornerRadius(size.height / 2f)
    drawRoundRect(fill, Offset(left, 0f), Size(width, size.height), r)
    val half = border / 2f
    drawRoundRect(
        Candy.Ink,
        Offset(left + half, half),
        Size(width - border, size.height - border),
        CornerRadius(size.height / 2f - half),
        style = Stroke(border),
    )
}

/** The design's stroke icons, on a 24-unit grid. */
object CandyIcons {
    val Freeze: ImageVector by lazy { strokeIcon(2.5f, "M12 2v20M4 6l16 12M20 6L4 18") }
    val Photo: ImageVector by lazy {
        strokeIcon(
            2.5f,
            "M6 4h12a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3z",
            "M7 10a2 2 0 1 0 4 0a2 2 0 1 0-4 0",
            "M21 16l-5-5-9 9",
        )
    }
    val Camera: ImageVector by lazy {
        strokeIcon(2.5f, "M4 8h3l2-3h6l2 3h3v11H4z", "M8.5 13a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0-7 0")
    }
    val Wand: ImageVector by lazy {
        strokeIcon(2.5f, "M4 20L14.5 9.5", "M16 3v4M14 5h4", "M20 10v3M18.5 11.5h3", "M8.5 3.5v2.5M7.25 4.75h2.5")
    }
    val Check: ImageVector by lazy { strokeIcon(3f, "M5 12.5l4.5 4.5L19 7.5") }
        val Close: ImageVector by lazy { strokeIcon(2.8f, "M6 6l12 12M18 6L6 18") }
    val Pencil: ImageVector by lazy { strokeIcon(2.4f, "M4 20h4L19 9l-4-4L4 16z", "M13.5 6.5l4 4") }
    val Reroll: ImageVector by lazy { strokeIcon(2.8f, "M20 12a8 8 0 1 1-2.3-5.7", "M20 4v5h-5") }
    val Play: ImageVector by lazy {
        ImageVector.Builder("play", 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes("M7 4l13 8-13 8z"), fill = SolidColor(Color.Black))
            .build()
    }

    /** Two slices lifting off a cake, for the permission screen. Drawn in its own colours. */
    val CakeSlice: ImageVector by lazy {
        ImageVector.Builder("cake", 104.dp, 104.dp, 104f, 104f)
            .addPath(
                addPathNodes("M52 52 L52 12 A40 40 0 1 0 92 52 Z"),
                fill = SolidColor(Color(0xFFFF8CC0)),
                stroke = SolidColor(Candy.Ink),
                strokeLineWidth = 4f,
                strokeLineJoin = StrokeJoin.Round,
            )
            .addPath(
                addPathNodes("M60 44 L60 4 A40 40 0 0 1 100 44 Z"),
                fill = SolidColor(Candy.Yellow),
                stroke = SolidColor(Candy.Ink),
                strokeLineWidth = 4f,
                strokeLineJoin = StrokeJoin.Round,
            )
            .addPath(
                addPathNodes("M52 52 L17 72 M52 52 L32 87"),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 3f,
                strokeLineCap = StrokeCap.Round,
            )
            .build()
    }

    private fun strokeIcon(width: Float, vararg paths: String): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                paths.forEach {
                    addPath(
                        addPathNodes(it),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = width,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
            .build()
}
