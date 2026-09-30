package com.unfaircake.cutter.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unfaircake.cutter.R
import com.unfaircake.cutter.domain.ShapeTransform
import com.unfaircake.cutter.ui.camera.CameraPreview
import com.unfaircake.cutter.ui.camera.PhotoBackground
import com.unfaircake.cutter.ui.components.CandyButton
import com.unfaircake.cutter.ui.components.CandyChip
import com.unfaircake.cutter.ui.components.CandyIcons
import com.unfaircake.cutter.ui.components.CssLines
import com.unfaircake.cutter.ui.components.hardShadow
import com.unfaircake.cutter.ui.components.hardTextShadow
import com.unfaircake.cutter.ui.components.rememberTick
import com.unfaircake.cutter.ui.controls.ControlActions
import com.unfaircake.cutter.ui.controls.ControlPanel
import com.unfaircake.cutter.ui.controls.PanelOverlap
import com.unfaircake.cutter.ui.overlay.CakeOverlay
import com.unfaircake.cutter.ui.theme.Bagel
import com.unfaircake.cutter.ui.theme.Bricolage
import com.unfaircake.cutter.ui.theme.Candy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
import com.unfaircake.cutter.ui.theme.ExactLineHeight

@Composable
fun CakeApp(viewModel: CakeViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Camera permission: asked once on first launch, re-checked every time the app resumes
    // (the user may have granted it from system settings).
    var hasCamera by remember { mutableStateOf(context.hasCameraPermission()) }
    var cameraBroken by remember { mutableStateOf(false) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCamera = it
    }
    LaunchedEffect(Unit) {
        if (!hasCamera && !askedOnce) {
            askedOnce = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasCamera = context.hasCameraPermission()
        cameraBroken = false // retry: the camera may have been busy in another app
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.showPhoto(uri)
    }
    val pickPhoto = {
        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    val snap = rememberSnapController(viewModel)

    // Easter egg: going down to one person throws sprinkles.
    var party by remember { mutableIntStateOf(0) }
    var lastCount by remember { mutableIntStateOf(state.peopleCount) }
    LaunchedEffect(state.peopleCount) {
        if (state.peopleCount == 1 && lastCount > 1) party++
        lastCount = state.peopleCount
    }
    // A new background means the old frame is gone: drop any snap in progress.
    LaunchedEffect(state.photoUri) { snap.reset() }

    // The long-press tip shows until "OK, got it", once per install.
    val prefs = remember(context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var showRigTip by remember { mutableStateOf(!prefs.getBoolean(KEY_RIG_TIP_DISMISSED, false)) }
    var showSnapTip by remember { mutableStateOf(!prefs.getBoolean(KEY_SNAP_TIP_DISMISSED, false)) }

    val actions = remember(viewModel) {
        ControlActions(
            onAddPerson = viewModel::addPerson,
            onRemovePerson = viewModel::removePerson,
            onUnfairnessChange = viewModel::setUnfairness,
            onReroll = viewModel::reroll,
            onShapeChange = viewModel::setShape,
            onSizeChange = viewModel::setSize,
            onRotationChange = viewModel::setRotation,
            onNameChange = viewModel::setName,
            onToggleFavorite = viewModel::toggleFavorite,
            onClearOutline = viewModel::clearOutline,
            onDismissSnapTip = {
                showSnapTip = false
                prefs.edit().putBoolean(KEY_SNAP_TIP_DISMISSED, true).apply()
            },
            onDismissRigTip = {
                showRigTip = false
                prefs.edit().putBoolean(KEY_RIG_TIP_DISMISSED, true).apply()
            },
        )
    }

    // Without the camera and without a photo there is nothing to cut: the whole screen asks.
    if (!hasCamera && state.photoUri == null) {
        PermissionScreen(
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = { context.openAppSettings() },
            onPickPhoto = pickPhoto,
        )
        return
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // Only visible behind the navigation bar, under the panel.
            .background(Candy.Cream)
            // The preview runs under the status bar; the panel stops above the navigation bar
            // and the keyboard.
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        // The panel's rounded top overlaps the bottom of the preview.
        val previewVisible = maxHeight * PREVIEW_SHARE
        PreviewArea(
            state = state,
            snap = snap,
            party = party,
            hasCamera = hasCamera && !cameraBroken,
            onCameraUnavailable = { cameraBroken = true },
            onToggleFreeze = viewModel::toggleFreeze,
            onFreezeFailed = viewModel::unfreeze,
            onPickPhoto = pickPhoto,
            onBackToCamera = viewModel::backToCamera,
            onPhotoFailed = {
                Toast.makeText(context, R.string.photo_load_failed, Toast.LENGTH_SHORT).show()
                viewModel.backToCamera()
            },
            onTransformChange = viewModel::setTransform,
            modifier = Modifier.fillMaxWidth().height(previewVisible + PanelOverlap),
        )
        ControlPanel(
            state = state,
            actions = actions,
            showRigTip = showRigTip,
            showSnapTip = showSnapTip,
            modifier = Modifier.fillMaxSize().padding(top = previewVisible),
        )
    }
}

private const val PREFS = "unfair_cake"
private const val KEY_RIG_TIP_DISMISSED = "rig_tip_dismissed"
private const val KEY_SNAP_TIP_DISMISSED = "snap_tip_dismissed"

/** Share of the screen height left visible for the camera, above the panel. */
private const val PREVIEW_SHARE = 0.54f

@Composable
private fun PreviewArea(
    state: CakeUiState,
    snap: SnapController,
    party: Int,
    hasCamera: Boolean,
    onCameraUnavailable: () -> Unit,
    onToggleFreeze: () -> Unit,
    onFreezeFailed: () -> Unit,
    onPickPhoto: () -> Unit,
    onBackToCamera: () -> Unit,
    onPhotoFailed: () -> Unit,
    onTransformChange: (ShapeTransform) -> Unit,
    modifier: Modifier = Modifier,
) {
    val photo = state.photoUri
    val currentShape by rememberUpdatedState(state.shape)
    Box(modifier.clipToBounds().background(Candy.Night)) {
        val showOverlay = when {
            photo != null -> {
                PhotoBackground(
                    uri = photo,
                    onLoadFailed = onPhotoFailed,
                    grabber = snap.grabber,
                    modifier = Modifier.fillMaxSize(),
                )
                true
            }

            hasCamera -> {
                CameraPreview(
                    frozen = state.frozen,
                    onCameraUnavailable = onCameraUnavailable,
                    onFreezeFailed = onFreezeFailed,
                    grabber = snap.grabber,
                    modifier = Modifier.fillMaxSize(),
                )
                true
            }

            else -> {
                // Permission is fine but the camera could not start (none, or in use).
                CenteredMessage(stringResource(R.string.camera_unavailable))
                false
            }
        }

        if (showOverlay) {
            CakeOverlay(
                shape = state.shape,
                transform = state.transform,
                shares = state.shares,
                percentLabels = state.people.map { it.percentText },
                description = state.people.joinToString { "${it.index + 1}: ${it.percentText}" },
                onTransformChange = onTransformChange,
                snapping = snap.aiming,
                onSnapTap = { position, size -> snap.onTap(position, size, state.shape) },
                snapToken = snap.token,
                freeform = state.outline,
                onHold = { position, size ->
                    snap.startHold(position, size, live = photo == null && hasCamera && !state.frozen) { currentShape }
                },
                onHoldMove = snap::moveHold,
                onHoldEnd = snap::endHold,
                // Leave the part hidden under the panel out of the cake's working area.
                modifier = Modifier.fillMaxSize().padding(bottom = PanelOverlap),
            )
        }

        snap.tap?.let { SearchPulse(it) }
        if (party > 0) key(party) { SprinkleBurst() }
        val tick = rememberTick()
        LaunchedEffect(snap.phase) {
            if (snap.phase == SnapPhase.Done || snap.phase == SnapPhase.Missed) tick()
        }
        LaunchedEffect(snap.holding) { if (snap.holding) tick() }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 18.dp, end = 18.dp, top = 18.dp),
        ) {
            PreviewHeader(
                status = when {
                    photo != null -> Status.Photo
                    hasCamera && state.frozen -> Status.Frozen
                    hasCamera -> Status.Live
                    else -> null
                },
            )
        }

        val sticker = when (snap.phase) {
            SnapPhase.Aim -> R.string.snap_aim
            SnapPhase.Busy -> R.string.snap_busy
            SnapPhase.Missed -> R.string.snap_missed
            SnapPhase.Done -> R.string.snap_done
            SnapPhase.Off -> if (photo == null && hasCamera && state.frozen) R.string.frozen_sticker else null
        }
        if (sticker != null) {
            // Tucked under the status chip, top right, clear of the cake in the middle.
            key(sticker) {
                Sticker(
                    stringResource(sticker),
                    Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 62.dp, end = 16.dp),
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
        ) {
            if (snap.aiming) {
                CandyButton(onClick = snap::cancel, container = Candy.Cream) {
                    Icon(CandyIcons.Close, contentDescription = null, tint = Candy.Ink, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.snap_cancel))
                }
                return@Row
            }
            when {
                photo != null -> CandyButton(onClick = onBackToCamera, container = Candy.Pink) {
                    Icon(CandyIcons.Camera, contentDescription = null, tint = Candy.Ink, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.back_to_camera))
                }

                hasCamera -> CandyButton(onClick = onToggleFreeze, container = Candy.Pink) {
                    Icon(
                        if (state.frozen) CandyIcons.Play else CandyIcons.Freeze,
                        contentDescription = null,
                        tint = Candy.Ink,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(stringResource(if (state.frozen) R.string.resume else R.string.freeze))
                }
            }
            if (photo != null || hasCamera) {
                CandyButton(
                    onClick = { snap.toggle(liveCamera = photo == null && hasCamera, frozen = state.frozen) },
                    container = Candy.Yellow,
                ) {
                    Icon(CandyIcons.Wand, contentDescription = null, tint = Candy.Ink, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.snap))
                }
            }
            CandyButton(onClick = onPickPhoto, container = Candy.Cream) {
                Icon(CandyIcons.Photo, contentDescription = null, tint = Candy.Ink, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.photo))
            }
        }
    }
}

private enum class Status { Live, Frozen, Photo }

@Composable
private fun PreviewHeader(status: Status?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        CssLines(
            text = stringResource(R.string.app_title_two_lines),
            style = TextStyle(
                fontFamily = Bagel,
                fontSize = 24.sp,
                lineHeight = 24.sp,
                color = Candy.Pink,
                shadow = hardTextShadow(2.dp, Candy.Ink),
            ),
            modifier = Modifier
                .weight(1f)
                .graphicsLayer {
                    rotationZ = -3f
                    transformOrigin = TransformOrigin(0f, 0.5f)
                },
        )
        LanguageFlag(Modifier.padding(end = 8.dp))
        when (status) {
            Status.Live -> CandyChip(stringResource(R.string.status_live), Candy.Pink)
            Status.Frozen -> CandyChip(stringResource(R.string.status_frozen), Candy.White)
            Status.Photo -> CandyChip(stringResource(R.string.status_photo), Candy.Yellow)
            null -> Unit
        }
    }
}

/** Tilted white label on the preview: frozen, snap instructions and results. */
@Composable
private fun Sticker(text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val slap = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { slap.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f)) }
    Text(
        text = text,
        fontFamily = Bagel,
        fontSize = 16.sp,
        color = Candy.Magenta,
        modifier = modifier
            .graphicsLayer {
                rotationZ = 4f + (1f - slap.value) * 10f
                scaleX = slap.value
                scaleY = slap.value
                alpha = slap.value.coerceIn(0f, 1f)
            }
            .hardShadow(3.dp, shape)
            .background(Candy.White, shape)
            .border(2.5.dp, Candy.Ink, shape)
            .padding(horizontal = 11.dp, vertical = 4.dp),
    )
}

private class Sprinkle(
    val vx: Float,
    val vy: Float,
    val spin: Float,
    val angle: Float,
    val color: Color,
)

/** A burst of candy sprinkles from the middle of the preview, falling back down. */
@Composable
private fun SprinkleBurst() {
    val tick = rememberTick()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch {
            repeat(3) {
                tick()
                delay(110)
            }
        }
        progress.animateTo(1f, tween(durationMillis = 2400, easing = LinearEasing))
    }
    val bits = remember {
        val colors = listOf(Candy.Pink, Candy.Yellow, Color(0xFF5CE1E6), Color(0xFFB388FF), Color(0xFF7CF29A), Color.White)
        List(110) {
            Sprinkle(
                vx = Random.nextFloat() * 2f - 1f,
                vy = -(0.6f + Random.nextFloat() * 0.9f),
                spin = (Random.nextFloat() * 2f - 1f) * 3f,
                angle = Random.nextFloat() * 360f,
                color = colors[it % colors.size],
            )
        }
    }
    if (progress.value >= 1f) return
    Canvas(Modifier.fillMaxSize()) {
        val t = progress.value * 2.4f
        val fade = ((1f - progress.value) / 0.25f).coerceIn(0f, 1f)
        val long = 10.dp.toPx()
        val thick = 3.2.dp.toPx()
        bits.forEach { b ->
            val x = size.width / 2f + b.vx * size.width * 0.55f * t
            val y = size.height * 0.45f + (b.vy * t + 0.9f * t * t) * size.height * 0.5f
            rotate(b.angle + b.spin * 360f * t, pivot = Offset(x, y)) {
                drawRoundRect(
                    color = b.color.copy(alpha = fade),
                    topLeft = Offset(x - long / 2f, y - thick / 2f),
                    size = Size(long, thick),
                    cornerRadius = CornerRadius(thick / 2f),
                )
            }
        }
    }
}

/** Rings rippling out from the tapped point while the outline is worked out. */
@Composable
private fun SearchPulse(center: Offset) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val t by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "ring",
    )
    Canvas(Modifier.fillMaxSize()) {
        val max = 56.dp.toPx()
        for (k in 0..1) {
            val p = (t + k * 0.5f) % 1f
            drawCircle(
                color = Candy.Pink.copy(alpha = 1f - p),
                radius = 10.dp.toPx() + p * max,
                center = center,
                style = Stroke(width = 4.dp.toPx() * (1f - p) + 1f),
            )
        }
        drawCircle(Candy.Ink, radius = 9.dp.toPx(), center = center)
        drawCircle(Candy.Pink, radius = 6.dp.toPx(), center = center)
        drawCircle(Color.White, radius = 6.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
    }
}

/** The current language's flag; a tap switches between English and French. */
@Composable
private fun LanguageFlag(modifier: Modifier = Modifier) {
    val french = LocalConfiguration.current.locales[0].language == "fr"
    CandyButton(
        onClick = {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(if (french) "en" else "fr"))
        },
        container = Candy.White,
        shape = CircleShape,
        height = 34.dp,
        shadow = 2.dp,
        border = 2.5.dp,
        horizontalPadding = 0.dp,
        contentDescription = stringResource(R.string.switch_language),
        modifier = modifier.size(34.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) { if (french) drawFrance() else drawUnion() }
    }
}

private fun DrawScope.drawFrance() {
    val third = size.width / 3f
    drawRect(Color(0xFF0055A4), size = Size(third, size.height))
    drawRect(Color.White, topLeft = Offset(third, 0f), size = Size(third, size.height))
    drawRect(Color(0xFFEF4135), topLeft = Offset(2 * third, 0f), size = Size(third, size.height))
}

/** Union Jack, simplified to read at 34 dp. */
private fun DrawScope.drawUnion() {
    val w = size.width
    val h = size.height
    drawRect(Color(0xFF012169))
    val red = Color(0xFFC8102E)
    drawLine(Color.White, Offset(0f, 0f), Offset(w, h), strokeWidth = w * 0.2f)
    drawLine(Color.White, Offset(w, 0f), Offset(0f, h), strokeWidth = w * 0.2f)
    drawLine(red, Offset(0f, 0f), Offset(w, h), strokeWidth = w * 0.07f)
    drawLine(red, Offset(w, 0f), Offset(0f, h), strokeWidth = w * 0.07f)
    drawRect(Color.White, topLeft = Offset(w * 0.35f, 0f), size = Size(w * 0.3f, h))
    drawRect(Color.White, topLeft = Offset(0f, h * 0.35f), size = Size(w, h * 0.3f))
    drawRect(red, topLeft = Offset(w * 0.41f, 0f), size = Size(w * 0.18f, h))
    drawRect(red, topLeft = Offset(0f, h * 0.41f), size = Size(w, h * 0.18f))
}

@Composable
private fun PermissionScreen(
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickPhoto: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Candy.Pink),
    ) {
        // Two soft blobs bleeding off opposite corners.
        Box(
            Modifier
                .offset(x = (-60).dp, y = (-40).dp)
                .size(220.dp)
                .background(Candy.PinkSoft, CircleShape),
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 80.dp, y = 60.dp)
                .size(280.dp)
                .background(Candy.PinkSoft, CircleShape),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 40.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(168.dp)
                    .hardShadow(8.dp, CircleShape)
                    .background(Candy.Cream, CircleShape)
                    .border(4.dp, Candy.Ink, CircleShape),
            ) {
                Image(rememberVectorPainter(CandyIcons.CakeSlice), contentDescription = null, modifier = Modifier.size(104.dp))
            }
            CssLines(
                text = stringResource(R.string.camera_permission_title),
                horizontalAlignment = Alignment.CenterHorizontally,
                style = TextStyle(
                    fontFamily = Bagel,
                    fontSize = 46.sp,
                    lineHeight = 47.sp,
                    color = Candy.Ink,
                    shadow = hardTextShadow(4.dp, Candy.Cream),
                ),
                modifier = Modifier.graphicsLayer { rotationZ = -2f },
            )
            Text(
                text = stringResource(R.string.camera_permission_body),
                textAlign = TextAlign.Center,
                fontFamily = Bricolage,
                fontSize = 17.sp,
                lineHeight = 24.6.sp,
                style = TextStyle(lineHeightStyle = ExactLineHeight),
                fontWeight = FontWeight.SemiBold,
                color = Candy.Ink,
                modifier = Modifier.widthIn(max = 310.dp),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth().padding(top = 6.dp),
            ) {
                CandyButton(
                    onClick = onRequestPermission,
                    container = Candy.Ink,
                    contentColor = Candy.Cream,
                    height = 56.dp,
                    shadow = 5.dp,
                    fontSize = 17,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.camera_permission_grant)) }
                // After "Don't ask again" the system dialog no longer shows, so settings is the way back.
                CandyButton(
                    onClick = onOpenSettings,
                    container = Candy.Cream,
                    height = 56.dp,
                    shadow = 5.dp,
                    fontSize = 17,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.camera_permission_settings)) }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clickable(role = Role.Button, onClick = onPickPhoto),
                ) {
                    Text(
                        text = stringResource(R.string.camera_permission_photo),
                        fontFamily = Bricolage,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Candy.Ink,
                        textDecoration = TextDecoration.Underline,
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().padding(32.dp).padding(bottom = PanelOverlap),
    ) {
        Text(
            text,
            color = Candy.Cream,
            fontFamily = Bricolage,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}
