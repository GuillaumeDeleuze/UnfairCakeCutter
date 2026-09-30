package com.unfaircake.cutter.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val TAG = "PhotoBackground"
private const val MAX_SIDE = 2048

/** Shows a picked photo, fitted to the preview area, for the overlay to sit on. */
@Composable
fun PhotoBackground(
    uri: Uri,
    onLoadFailed: () -> Unit,
    grabber: FrameGrabber,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentOnLoadFailed by rememberUpdatedState(onLoadFailed)
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uri) {
        val loaded = withContext(Dispatchers.IO) { loadBitmap(context, uri) }
        value = loaded?.asImageBitmap()
        if (loaded == null) currentOnLoadFailed()
    }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    DisposableEffect(grabber) {
        grabber.source = { bitmap?.let { renderFitted(it.asAndroidBitmap(), boxSize) } }
        onDispose { grabber.source = null }
    }
    Box(modifier.onSizeChanged { boxSize = it }) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** [photo] drawn the way [ContentScale.Fit] shows it in a box of [size], bars included. */
private fun renderFitted(photo: Bitmap, size: IntSize): Bitmap? {
    if (size.width <= 0 || size.height <= 0 || photo.width <= 0 || photo.height <= 0) return null
    val scale = min(size.width.toFloat() / photo.width, size.height.toFloat() / photo.height)
    val w = photo.width * scale
    val h = photo.height * scale
    val left = (size.width - w) / 2f
    val top = (size.height - h) / 2f
    return Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888).also {
        val canvas = android.graphics.Canvas(it)
        // Same colour as the empty preview behind the photo.
        canvas.drawColor(android.graphics.Color.rgb(0x1C, 0x06, 0x14))
        canvas.drawBitmap(photo, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
    }
}

/** Decodes [uri] upright and at most [MAX_SIDE] px on its long side; null on any failure. */
private fun loadBitmap(context: Context, uri: Uri): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        // ImageDecoder applies the EXIF orientation itself.
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val largest = max(w, h)
            if (largest > MAX_SIDE) {
                val scale = MAX_SIDE.toFloat() / largest
                decoder.setTargetSize((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1))
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        loadBitmapLegacy(context, uri)
    }
} catch (e: Exception) {
    Log.w(TAG, "Could not decode $uri", e)
    null
}

/** API 26–27: BitmapFactory with power-of-two downsampling, then EXIF rotation. */
private fun loadBitmapLegacy(context: Context, uri: Uri): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null

    val orientation = resolver.openInputStream(uri)?.use {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (degrees == 0f) return decoded
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
}
