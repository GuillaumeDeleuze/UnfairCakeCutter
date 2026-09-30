package com.unfaircake.cutter.ui.camera

import android.graphics.Bitmap

/**
 * Hands out the picture currently behind the overlay, at the preview's pixel size and crop, so a
 * tap on screen lands on the same pixel in it. The camera or photo background fills it in.
 */
class FrameGrabber {
    internal var source: (() -> Bitmap?)? = null

    fun grab(): Bitmap? = source?.invoke()
}
