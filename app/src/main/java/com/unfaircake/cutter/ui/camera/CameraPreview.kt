package com.unfaircake.cutter.ui.camera

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

private const val TAG = "CameraPreview"

/**
 * Full-size rear camera preview. When [frozen] is true the last displayed frame is captured and
 * shown on top, so the cuts can be checked on a still image; the live preview resumes as soon as
 * [frozen] goes back to false.
 */
@Composable
fun CameraPreview(
    frozen: Boolean,
    onCameraUnavailable: () -> Unit,
    onFreezeFailed: () -> Unit,
    grabber: FrameGrabber,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnUnavailable by rememberUpdatedState(onCameraUnavailable)
    val currentOnFreezeFailed by rememberUpdatedState(onFreezeFailed)

    val previewView = remember {
        PreviewView(context).apply {
            // TextureView-backed so the frame can be grabbed for Freeze and it composes cleanly
            // under the Compose overlay.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner, previewView) {
        var disposed = false
        var provider: ProcessCameraProvider? = null
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                if (disposed) return@addListener
                try {
                    val cameraProvider = future.get()
                    provider = cameraProvider
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not start the rear camera", e)
                    currentOnUnavailable()
                }
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            disposed = true
            provider?.unbindAll()
        }
    }

    var frozenBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val frozenFrame: ImageBitmap? = remember(frozenBitmap) { frozenBitmap?.asImageBitmap() }
    LaunchedEffect(frozen) {
        frozenBitmap = if (frozen) previewView.bitmap else null
        // No frame yet (camera still starting): don't pretend to be frozen.
        if (frozen && frozenBitmap == null) currentOnFreezeFailed()
    }

    // The still frame when frozen (what the user is looking at), else the live one.
    DisposableEffect(grabber) {
        grabber.source = { frozenBitmap ?: previewView.bitmap }
        onDispose { grabber.source = null }
    }

    Box(modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        frozenFrame?.let { frame ->
            // PreviewView.getBitmap() already matches the view's size and crop.
            Image(
                bitmap = frame,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
