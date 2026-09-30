package com.unfaircake.cutter.ui.controls

import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.unfaircake.cutter.ui.components.hardShadow
import com.unfaircake.cutter.ui.components.pressProgress
import com.unfaircake.cutter.ui.components.rememberTick
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.unfaircake.cutter.R
import com.unfaircake.cutter.domain.CakeShape
import com.unfaircake.cutter.domain.MAX_PEOPLE
import com.unfaircake.cutter.domain.MAX_SHAPE_SIZE
import com.unfaircake.cutter.domain.MIN_PEOPLE
import com.unfaircake.cutter.domain.MIN_SHAPE_SIZE
import com.unfaircake.cutter.domain.Shares
import com.unfaircake.cutter.domain.Verdict
import com.unfaircake.cutter.ui.CakeUiState
import com.unfaircake.cutter.ui.PersonUi
import com.unfaircake.cutter.ui.components.CandyButton
import com.unfaircake.cutter.ui.components.CandyCircleButton
import com.unfaircake.cutter.ui.components.CandyIcons
import com.unfaircake.cutter.ui.components.CandySegmented
import com.unfaircake.cutter.ui.components.CandySlider
import com.unfaircake.cutter.ui.components.CssLines
import com.unfaircake.cutter.ui.components.FitText
import com.unfaircake.cutter.ui.components.Pill
import com.unfaircake.cutter.ui.components.SectionLabel
import com.unfaircake.cutter.ui.components.SliderLook
import com.unfaircake.cutter.ui.components.hardTextShadow
import com.unfaircake.cutter.ui.theme.Bagel
import com.unfaircake.cutter.ui.theme.Bricolage
import com.unfaircake.cutter.ui.theme.Candy
import com.unfaircake.cutter.ui.theme.sliceColor
import kotlin.math.abs
import kotlin.math.roundToInt

/** Everything the panel can ask the ViewModel to do. */
class ControlActions(
    val onAddPerson: () -> Unit,
    val onRemovePerson: () -> Unit,
    val onUnfairnessChange: (Int) -> Unit,
    val onReroll: () -> Unit,
    val onShapeChange: (CakeShape) -> Unit,
    val onSizeChange: (Float) -> Unit,
    val onRotationChange: (Float) -> Unit,
    val onNameChange: (Int, String) -> Unit,
    val onToggleFavorite: (Int) -> Unit,
    val onClearOutline: () -> Unit,
    val onDismissRigTip: () -> Unit,
    val onDismissSnapTip: () -> Unit,
)

/** How far the panel's rounded top rides up over the camera preview. */
val PanelOverlap = 26.dp

private val PanelCorner = 30.dp

@Composable
fun ControlPanel(
    state: CakeUiState,
    actions: ControlActions,
    showRigTip: Boolean,
    showSnapTip: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(topStart = PanelCorner, topEnd = PanelCorner)
    Box(
        modifier
            .clip(shape)
            .background(Candy.Cream)
            .drawBehind {
                // Ink rule over the top and its rounded corners, then down both sides.
                val w = 3.dp.toPx()
                val r = PanelCorner.toPx()
                val half = w / 2f
                val edge = Path().apply {
                    moveTo(half, size.height)
                    lineTo(half, r)
                    arcTo(Rect(half, half, 2 * r - half, 2 * r - half), 180f, 90f, false)
                    lineTo(size.width - r, half)
                    arcTo(Rect(size.width - 2 * r + half, half, size.width - half, 2 * r - half), 270f, 90f, false)
                    lineTo(size.width - half, size.height)
                }
                drawPath(edge, Candy.Ink, style = Stroke(width = w))
            },
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = PanelOverlap, bottom = 28.dp),
            modifier = Modifier.fillMaxHeight(),
        ) {
            // One tip at a time: how to snap first, then the long-press trick.
            if (showSnapTip) {
                item(key = "snap-tip") {
                    TipCard(
                        title = stringResource(R.string.snap_tip_title),
                        body = stringResource(R.string.snap_tip_body),
                        color = Candy.PinkSoft,
                        onDismiss = actions.onDismissSnapTip,
                        modifier = Modifier.animateItem().padding(bottom = 20.dp),
                    )
                }
            } else if (showRigTip) {
                item(key = "rig-tip") {
                    TipCard(
                        title = stringResource(R.string.rig_tip_title),
                        body = stringResource(R.string.rig_tip_body),
                        color = Candy.Yellow,
                        onDismiss = actions.onDismissRigTip,
                        modifier = Modifier.animateItem().padding(bottom = 20.dp),
                    )
                }
            }
            item(key = "unfairness", contentType = "unfairness") { UnfairnessHeader(state) }
            item(key = "slider") { UnfairnessSlider(state, actions, Modifier.padding(top = 20.dp)) }
            item(key = "people") { PeopleRow(state.peopleCount, actions, Modifier.padding(top = 20.dp)) }
            item(key = "shape") { ShapeControls(state, actions, Modifier.padding(top = 20.dp)) }
            item(key = "who") {
                SectionLabel(stringResource(R.string.who_gets_what), Modifier.padding(top = 20.dp))
            }
            val solo = state.peopleCount == 1
            val winner = when {
                solo -> 0
                state.everyoneGetsTheSame -> -1
                else -> state.people.maxByOrNull { it.share }?.index ?: -1
            }
            items(state.people, key = { "person-${it.index}" }) { person ->
                PersonRow(
                    person = person,
                    maxShare = state.maxShare,
                    isWinner = person.index == winner,
                    badge = if (solo) R.string.all_of_it else R.string.winner,
                    isFavorite = person.index == state.favorite,
                    onNameChange = { actions.onNameChange(person.index, it) },
                    onToggleFavorite = { actions.onToggleFavorite(person.index) },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** One-time hint, until "OK, got it". */
@Composable
private fun TipCard(title: String, body: String, color: Color, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .hardShadow(4.dp, shape)
            .background(color, shape)
            .border(2.5.dp, Candy.Ink, shape)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Text(
            title,
            fontFamily = Bagel,
            fontSize = 22.sp,
            color = Candy.Ink,
            modifier = Modifier.graphicsLayer { rotationZ = -3f },
        )
        Text(
            body,
            fontFamily = Bricolage,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = Candy.Ink,
        )
        CandyButton(
            onClick = onDismiss,
            container = Candy.Ink,
            contentColor = Candy.Cream,
            height = 40.dp,
            shadow = 3.dp,
            horizontalPadding = 16.dp,
            fontSize = 14,
            modifier = Modifier.align(Alignment.End),
        ) { Text(stringResource(R.string.tip_ok)) }
    }
}

@Composable
private fun UnfairnessHeader(state: CakeUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SectionLabel(stringResource(R.string.unfairness), Modifier.weight(1f))
            CssLines(
                text = state.unfairness.toString(),
                style = TextStyle(fontFamily = Bagel, fontSize = 30.sp, lineHeight = 30.sp, color = Candy.Ink),
            )
        }
        // Easter egg: cutting a cake for one.
        val solo = state.peopleCount == 1
        val verdict = stringResource(if (solo) R.string.verdict_all_mine else state.verdict.label())
        // Each new verdict lands with a wobble and a tick.
        val pop = remember { Animatable(1f) }
        val tick = rememberTick()
        var lastVerdict by remember { mutableStateOf(verdict) }
        LaunchedEffect(verdict) {
            if (verdict == lastVerdict) return@LaunchedEffect
            lastVerdict = verdict
            tick()
            pop.snapTo(1.18f)
            pop.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 380f))
        }
        // Short verdicts get the big size, longer ones 40, and anything still too wide for one
        // line ("Honteusement biaisé") shrinks until it fits.
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        var width by remember { mutableIntStateOf(0) }
        val verdictSize = remember(verdict, width) {
            var size = if (verdict.length <= 11) 52 else 40
            val room = width - with(density) { 10.dp.roundToPx() }
            while (width > 0 && size > 26 &&
                measurer.measure(verdict, TextStyle(fontFamily = Bagel, fontSize = size.sp)).size.width > room
            ) {
                size -= 2
            }
            size
        }
        CssLines(
            text = verdict,
            style = TextStyle(
                fontFamily = Bagel,
                fontSize = verdictSize.sp,
                lineHeight = verdictSize.sp,
                color = Candy.Ink,
                shadow = hardTextShadow(4.dp, Candy.Pink),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { width = it.width }
                .padding(top = 6.dp, bottom = 4.dp)
                .graphicsLayer {
                    rotationZ = -2.5f - (pop.value - 1f) * 20f
                    scaleX = pop.value
                    scaleY = pop.value
                    transformOrigin = TransformOrigin(0f, 0.5f)
                },
        )
        val ratio = if (solo) {
            stringResource(R.string.party_of_one)
        } else if (state.everyoneGetsTheSame) {
            stringResource(R.string.everyone_same)
        } else {
            stringResource(R.string.biggest_vs_smallest, Shares.formatRatio(state.maxMinRatio))
        }
        Text(ratio, fontFamily = Bricolage, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Candy.Ink)
    }
}

private fun Verdict.label(): Int = when (this) {
    Verdict.PERFECTLY_FAIR -> R.string.verdict_fair
    Verdict.SLIGHTLY_GENEROUS -> R.string.verdict_slightly
    Verdict.SUSPICIOUS -> R.string.verdict_suspicious
    Verdict.SHAMELESSLY_BIASED -> R.string.verdict_biased
    Verdict.CAKE_TYRANNY -> R.string.verdict_tyranny
}

@Composable
private fun UnfairnessSlider(state: CakeUiState, actions: ControlActions, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier.fillMaxWidth()) {
        val label = stringResource(R.string.unfairness)
        CandySlider(
            value = state.unfairness.toFloat(),
            onValueChange = { actions.onUnfairnessChange(it.roundToInt()) },
            valueRange = 0f..100f,
            steps = 99,
            look = SliderLook.Big,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            listOf(R.string.scale_fair, R.string.scale_suspicious, R.string.scale_tyranny).forEach {
                Text(
                    stringResource(it),
                    fontFamily = Bricolage,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Candy.Muted,
                )
            }
        }
    }
}

@Composable
private fun PeopleRow(count: Int, actions: ControlActions, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .clip(Pill)
                .background(Candy.White)
                .border(3.dp, Candy.Ink, Pill)
                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        ) {
            // "Personnes" needs more room than "People": it shrinks a little instead of wrapping.
            FitText(
                stringResource(R.string.people),
                style = TextStyle(fontFamily = Bricolage, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Candy.Ink),
                modifier = Modifier.weight(1f).padding(end = 6.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CandyCircleButton(
                    onClick = actions.onRemovePerson,
                    container = Candy.Cream,
                    contentDescription = stringResource(R.string.decrease_people),
                    enabled = count > MIN_PEOPLE,
                ) { Text("−") }
                val pop = remember { Animatable(1f) }
                var lastCount by remember { mutableIntStateOf(count) }
                LaunchedEffect(count) {
                    if (count == lastCount) return@LaunchedEffect
                    val up = count > lastCount
                    lastCount = count
                    pop.snapTo(if (up) 1.45f else 0.6f)
                    pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 520f))
                }
                Text(
                    text = count.toString(),
                    fontFamily = Bagel,
                    fontSize = 28.sp,
                    textAlign = TextAlign.Center,
                    color = Candy.Ink,
                    modifier = Modifier
                        .width(34.dp)
                        .graphicsLayer {
                            scaleX = pop.value
                            scaleY = pop.value
                        },
                )
                CandyCircleButton(
                    onClick = actions.onAddPerson,
                    container = Candy.Pink,
                    contentDescription = stringResource(R.string.increase_people),
                    enabled = count < MAX_PEOPLE,
                ) { Text("+") }
            }
        }
        val spin = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        CandyButton(
            onClick = {
                actions.onReroll()
                scope.launch { spin.animateTo(spin.value + 360f, spring(dampingRatio = 0.55f, stiffness = 180f)) }
            },
            container = Candy.Yellow,
            height = 56.dp,
            horizontalPadding = 18.dp,
        ) {
            Icon(
                CandyIcons.Reroll,
                contentDescription = null,
                tint = Candy.Ink,
                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = spin.value },
            )
            Text(stringResource(R.string.reroll))
        }
    }
}

@Composable
private fun ShapeControls(state: CakeUiState, actions: ControlActions, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.shape))
        val isRound = state.shape.isRound
        CandySegmented(
            options = listOf(true to stringResource(R.string.shape_round), false to stringResource(R.string.shape_rectangle)),
            selected = isRound,
            onSelect = { round ->
                if (round) actions.onShapeChange(CakeShape.ROUND) else if (isRound) actions.onShapeChange(CakeShape.TRAY_GRID)
            },
        )
        if (!isRound) {
            CandySegmented(
                options = listOf(
                    CakeShape.TRAY_STRIPS to stringResource(R.string.layout_strips),
                    CakeShape.TRAY_GRID to stringResource(R.string.layout_grid),
                    CakeShape.LOG to stringResource(R.string.layout_log),
                ),
                selected = state.shape,
                onSelect = actions.onShapeChange,
            )
        }
        if (state.outline != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Candy.Track, Pill)
                    .border(2.5.dp, Candy.Ink, Pill)
                    .padding(start = 14.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
            ) {
                Icon(CandyIcons.Wand, contentDescription = null, tint = Candy.Ink, modifier = Modifier.size(16.dp))
                Text(
                    stringResource(R.string.snapped_outline),
                    fontFamily = Bricolage,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Candy.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                CandyButton(
                    onClick = actions.onClearOutline,
                    container = Candy.White,
                    height = 36.dp,
                    shadow = 2.dp,
                    border = 2.5.dp,
                    horizontalPadding = 14.dp,
                    fontSize = 14,
                ) { Text(stringResource(R.string.snapped_outline_reset)) }
            }
        }
        LabeledSlider(
            label = stringResource(R.string.size),
            valueText = "${(state.transform.size * 100).roundToInt()}%",
            value = state.transform.size,
            range = MIN_SHAPE_SIZE..MAX_SHAPE_SIZE,
            onChange = actions.onSizeChange,
        )
        val degrees = state.transform.rotationDeg.roundToInt()
        LabeledSlider(
            label = stringResource(R.string.rotation),
            // A real minus sign, as in the design.
            valueText = stringResource(R.string.rotation_value, if (degrees < 0) "−${abs(degrees)}" else "$degrees"),
            value = state.transform.rotationDeg,
            range = -180f..180f,
            onChange = actions.onRotationChange,
        )
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            fontFamily = Bricolage,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Candy.Ink,
            modifier = Modifier.width(66.dp),
        )
        CandySlider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            look = SliderLook.Small,
            modifier = Modifier.weight(1f).semantics { contentDescription = label },
        )
        Text(
            valueText,
            fontFamily = Bricolage,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Candy.Ink,
            textAlign = TextAlign.End,
            modifier = Modifier.width(44.dp),
        )
    }
}

// combinedClickable is still marked experimental in this Compose version.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PersonRow(
    person: PersonUi,
    maxShare: Double,
    isWinner: Boolean,
    badge: Int,
    isFavorite: Boolean,
    onNameChange: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = sliceColor(person.index)
    val cardShape = RoundedCornerShape(18.dp)
    val fieldLabel = stringResource(R.string.person_name_label, person.index + 1)
    // Person 1 holds the phone.
    val placeholder = if (person.index == 0) stringResource(R.string.default_me) else stringResource(R.string.person_default_name, person.index + 1)
    val nameStyle = TextStyle(fontFamily = Bricolage, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Candy.Ink)
    val density = LocalDensity.current

    // The name is plain text until the card is tapped, so a long press anywhere on the card,
    // name included, reaches the card instead of starting a text selection.
    var editing by rememberSaveable(person.index) { mutableStateOf(false) }
    var field by remember(person.index) { mutableStateOf(TextFieldValue(person.name)) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(editing) {
        if (editing) {
            field = field.copy(selection = TextRange(field.text.length))
            focusRequester.requestFocus()
        }
    }

    // Tap: squash, then float off the page on a pink shadow while editing, the dot tilted.
    // Long press: a wiggle, and the slices shuffle so this person comes out on top.
    val interaction = remember { MutableInteractionSource() }
    val press = pressProgress(interaction)
    val lift by animateFloatAsState(
        targetValue = if (editing) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 420f),
        label = "lift",
    )
    val wiggle = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val rigLabel = stringResource(if (isFavorite) R.string.unrig_action else R.string.rig_action)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val up = with(density) { 3.dp.toPx() } * lift
                translationX = -up
                translationY = -up
                val squash = 1f - 0.025f * press
                scaleX = squash
                scaleY = squash
                rotationZ = wiggle.value
            }
            .hardShadow(5.dp * lift.coerceAtLeast(0f), cardShape, Candy.Pink)
            .clip(cardShape)
            .background(Candy.White)
            .border(2.5.dp, Candy.Ink, cardShape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = fieldLabel,
                onLongClickLabel = rigLabel,
                onLongClick = {
                    focusManager.clearFocus()
                    onToggleFavorite()
                    scope.launch {
                        for (angle in listOf(-4f, 3.5f, -2.5f, 1.5f)) {
                            wiggle.animateTo(angle, tween(durationMillis = 55))
                        }
                        wiggle.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 500f))
                    }
                },
                onClick = { editing = true },
            )
            .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .graphicsLayer {
                    rotationZ = -14f * lift
                    val grow = 1f + 0.1f * lift
                    scaleX = grow
                    scaleY = grow
                }
                .background(color, CircleShape)
                .border(2.5.dp, Candy.Ink, CircleShape),
        ) {
            Text("${person.index + 1}", fontFamily = Bagel, fontSize = 16.sp, color = Candy.Ink)
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (editing) {
                    var wasFocused by remember { mutableStateOf(false) }
                    BasicTextField(
                        value = field,
                        onValueChange = {
                            field = if (it.text.length > MAX_NAME_LENGTH) {
                                it.copy(text = it.text.take(MAX_NAME_LENGTH), selection = TextRange(MAX_NAME_LENGTH))
                            } else {
                                it
                            }
                            onNameChange(field.text)
                        },
                        singleLine = true,
                        textStyle = nameStyle,
                        cursorBrush = SolidColor(Candy.Pink),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        decorationBox = { inner ->
                            Box {
                                if (field.text.isEmpty()) {
                                    Text(placeholder, style = nameStyle.copy(color = Candy.Muted), maxLines = 1)
                                }
                                inner()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .onFocusChanged {
                                // Leaving the field (Done, back, another card) ends the edit.
                                if (it.isFocused) wasFocused = true else if (wasFocused) editing = false
                            }
                            .semantics { contentDescription = fieldLabel },
                    )
                } else {
                    Text(
                        text = field.text.ifEmpty { placeholder },
                        style = nameStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        CandyIcons.Pencil,
                        contentDescription = null,
                        tint = Candy.Muted,
                        modifier = Modifier.padding(start = 5.dp).size(13.dp),
                    )
                    if (isWinner) {
                        Text(
                            text = stringResource(badge),
                            fontFamily = Bricolage,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.1.em,
                            color = Candy.Yellow,
                            maxLines = 1,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .background(Candy.Ink, Pill)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            val target = if (maxShare > 0.0) (person.share / maxShare).toFloat().coerceIn(0f, 1f) else 0f
            val fraction by animateFloatAsState(
                targetValue = target,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 220f),
                label = "bar",
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(Candy.Track, Pill)
                    .drawBehind {
                        val f = fraction.coerceIn(0f, 1.04f)
                        if (f <= 0f) return@drawBehind
                        val w = (size.width * f).coerceAtLeast(size.height)
                        drawRoundRect(color, Offset.Zero, Size(w, size.height), CornerRadius(size.height / 2f))
                        // The design's 1.5dp inset ink outline on the filled part.
                        val b = 1.5.dp.toPx()
                        drawRoundRect(
                            Candy.Ink,
                            Offset(b / 2f, b / 2f),
                            Size(w - b, size.height - b),
                            CornerRadius(size.height / 2f - b / 2f),
                            style = Stroke(b),
                        )
                    },
            )
        }
        Text(
            text = person.percentText,
            fontFamily = Bagel,
            fontSize = 20.sp,
            textAlign = TextAlign.End,
            color = Candy.Ink,
            modifier = Modifier.width(62.dp),
        )
    }
}

private const val MAX_NAME_LENGTH = 24
