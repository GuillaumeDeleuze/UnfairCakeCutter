package com.unfaircake.cutter.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.unfaircake.cutter.domain.CakeShape
import com.unfaircake.cutter.domain.SnapFit
import com.unfaircake.cutter.snap.CakeSnapper
import com.unfaircake.cutter.ui.camera.FrameGrabber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SnapPhase {
    Off,

    /** Waiting for the user to tap the cake. */
    Aim,

    /** Working out the outline around the tap. */
    Busy,

    /** Nothing cake-like there; the user can tap again. */
    Missed,

    /** Outline applied; shown briefly, then back to [Off]. */
    Done,

    /** Outline applied from a tap: more taps add to it, until the user says done. */
    Refine,
}

/**
 * Tap-to-snap: freezes the live camera, waits for a tap on the cake, finds its outline and hands
 * it to the ViewModel. Session-only UI state, so it lives here rather than in the ViewModel.
 */
@Stable
class SnapController(private val scope: CoroutineScope, private val viewModel: CakeViewModel) {

    var phase by mutableStateOf(SnapPhase.Off)
        private set

    /** Where the user tapped, in overlay pixels, while [phase] is [SnapPhase.Busy]. */
    var tap by mutableStateOf<Offset?>(null)
        private set

    /** Bumped on every applied snap, so the overlay glides to the new outline. */
    var token by mutableIntStateOf(0)
        private set

    val grabber = FrameGrabber()

    /** Taps on the preview go to the snap (aim, add), not to the outline's drag and pinch. */
    val aiming: Boolean get() = phase == SnapPhase.Aim || phase == SnapPhase.Busy || phase == SnapPhase.Missed || phase == SnapPhase.Refine

    /** Still looking for the cake: the guides step back so it shows. Once found, they're back. */
    val searching: Boolean get() = aiming && refine == null

    /** The cake found so far in this snap session (same still picture), grown by each tap. */
    private var refine: CakeSnapper.Segment? = null

    /** A finger is held on the preview: the outline follows the cake under it, live. */
    var holding by mutableStateOf(false)
        private set

    /** Snap froze the camera itself, so cancelling should let it run again. */
    private var froze = false
    private var job: Job? = null
    private var holdTarget = Offset.Zero

    fun toggle(liveCamera: Boolean, frozen: Boolean) {
        if (aiming) {
            cancel()
            return
        }
        job?.cancel()
        if (liveCamera && !frozen) {
            viewModel.freeze()
            froze = true
        }
        tap = null
        refine = null
        phase = SnapPhase.Aim
    }

    /** Ends a snap session, keeping the outline (and the frozen picture it fits). */
    fun done() {
        job?.cancel()
        refine = null
        tap = null
        phase = SnapPhase.Off
    }

    fun cancel() {
        job?.cancel()
        holding = false
        refine = null
        phase = SnapPhase.Off
        tap = null
        if (froze) viewModel.unfreeze()
        froze = false
    }

    /** The background changed under us (photo picked, back to camera): start over. */
    fun reset() {
        job?.cancel()
        holding = false
        refine = null
        phase = SnapPhase.Off
        tap = null
        froze = false
    }

    /**
     * Press and hold: snaps to the cake under the finger, then keeps re-snapping on the live
     * picture until the finger lifts, so the outline follows the cake as the phone moves. On a
     * still picture (frozen, photo) one snap is enough. The kind of cut is settled by the first
     * snap so it doesn't flicker between wedges and strips.
     */
    fun startHold(position: Offset, overlaySize: IntSize, live: Boolean, currentShape: () -> CakeShape) {
        if (aiming || holding) return
        job?.cancel()
        holding = true
        holdTarget = position
        tap = position
        phase = SnapPhase.Off
        job = scope.launch {
            var shape: CakeShape? = null
            // The first snap settles the turn; later ones are framed at the same angle.
            var turn: Double? = null
            while (holding) {
                val target = holdTarget
                val frame = grabber.grab()
                val result = frame?.let { withContext(Dispatchers.Default) { CakeSnapper.snap(it, target.x, target.y) } }
                    ?.let { r -> turn?.let { SnapFit.reframe(r, it) } ?: keepTurn(r).also { turn = it.rotationDeg } }
                if (result != null) {
                    val kind = shape ?: SnapFit.shapeFor(result, currentShape()).also { shape = it }
                    viewModel.applySnap(
                        kind,
                        SnapFit.toTransform(result, overlaySize.width.toFloat(), overlaySize.height.toFloat()),
                        result.outline,
                    )
                    token++
                    phase = SnapPhase.Done
                } else if (shape == null) {
                    phase = SnapPhase.Missed
                }
                if (!live) break
                delay(HOLD_REFRESH_MS)
            }
        }
    }

    /** Turn the new outline as little as possible from where the current one stands. */
    private fun keepTurn(result: SnapFit.Result): SnapFit.Result =
        SnapFit.alignTo(result, viewModel.uiState.value.transform.rotationDeg.toDouble())

    fun moveHold(position: Offset) {
        if (!holding) return
        holdTarget = position
        tap = position
    }

    fun endHold() {
        if (!holding) return
        holding = false
        tap = null
        val shown = phase
        scope.launch {
            delay(1200)
            if (!holding && !aiming && phase == shown) phase = SnapPhase.Off
        }
    }

    fun onTap(position: Offset, overlaySize: IntSize, currentShape: CakeShape) {
        if (phase != SnapPhase.Aim && phase != SnapPhase.Missed && phase != SnapPhase.Refine) return
        phase = SnapPhase.Busy
        tap = position
        val before = refine
        job = scope.launch {
            // The overlay and the frame share their top-left corner and pixel scale.
            val frame = grabber.grab()
            val found = frame?.let { withContext(Dispatchers.Default) { CakeSnapper.segment(it, position.x, position.y) } }
            // A later tap adds to what the first ones found, on the same still picture.
            val grown = if (before != null && found != null && found.width == before.width && found.height == before.height) {
                CakeSnapper.Segment(
                    SnapFit.merge(before.mask, found.mask, before.width, before.height),
                    before.width, before.height, before.scale, before.tapX, before.tapY,
                )
            } else {
                found
            }
            val result = grown?.let { withContext(Dispatchers.Default) { CakeSnapper.fit(it) } }?.let(::keepTurn)
            tap = null
            if (grown == null || result == null) {
                phase = SnapPhase.Missed
                return@launch
            }
            viewModel.applySnap(
                SnapFit.shapeFor(result, currentShape),
                SnapFit.toTransform(result, overlaySize.width.toFloat(), overlaySize.height.toFloat()),
                result.outline,
            )
            token++
            // The frame stays frozen: the outline was fitted to it.
            froze = false
            refine = grown
            phase = SnapPhase.Refine
        }
    }
}

/** Pause between two live snaps while a finger is held. */
private const val HOLD_REFRESH_MS = 300L

@Composable
fun rememberSnapController(viewModel: CakeViewModel): SnapController {
    val scope = rememberCoroutineScope()
    return remember(viewModel) { SnapController(scope, viewModel) }
}
