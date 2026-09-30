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

    val aiming: Boolean get() = phase == SnapPhase.Aim || phase == SnapPhase.Busy || phase == SnapPhase.Missed

    /** Snap froze the camera itself, so cancelling should let it run again. */
    private var froze = false
    private var job: Job? = null

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
        phase = SnapPhase.Aim
    }

    fun cancel() {
        job?.cancel()
        phase = SnapPhase.Off
        tap = null
        if (froze) viewModel.unfreeze()
        froze = false
    }

    /** The background changed under us (photo picked, back to camera): start over. */
    fun reset() {
        job?.cancel()
        phase = SnapPhase.Off
        tap = null
        froze = false
    }

    fun onTap(position: Offset, overlaySize: IntSize, currentShape: CakeShape) {
        if (phase != SnapPhase.Aim && phase != SnapPhase.Missed) return
        phase = SnapPhase.Busy
        tap = position
        job = scope.launch {
            // The overlay and the frame share their top-left corner and pixel scale.
            val frame = grabber.grab()
            val result = frame?.let { withContext(Dispatchers.Default) { CakeSnapper.snap(it, position.x, position.y) } }
            tap = null
            if (result == null) {
                phase = SnapPhase.Missed
                return@launch
            }
            viewModel.applySnap(
                SnapFit.shapeFor(result, currentShape),
                SnapFit.toTransform(result, overlaySize.width.toFloat(), overlaySize.height.toFloat()),
            )
            token++
            // The frame stays frozen: the outline was fitted to it.
            froze = false
            phase = SnapPhase.Done
            delay(1400)
            phase = SnapPhase.Off
        }
    }
}

@Composable
fun rememberSnapController(viewModel: CakeViewModel): SnapController {
    val scope = rememberCoroutineScope()
    return remember(viewModel) { SnapController(scope, viewModel) }
}
