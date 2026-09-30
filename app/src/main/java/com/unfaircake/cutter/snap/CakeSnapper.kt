package com.unfaircake.cutter.snap

import android.graphics.Bitmap
import android.util.Log
import com.unfaircake.cutter.domain.SnapFit
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Finds the cake the user tapped on a still frame: GrabCut segmentation (OpenCV, on device),
 * then [SnapFit] for the outline. Everything stays on the phone.
 */
object CakeSnapper {

    private const val TAG = "CakeSnapper"

    /** Work on a small copy: plenty for an outline, and GrabCut stays well under a second. */
    private const val WORK_SIDE = 320
    private const val ITERATIONS = 5

    /** Around the tap: surely cake close in, probably cake further out (shares of the short side). */
    private const val SURE_RADIUS = 0.04
    private const val LIKELY_RADIUS = 0.22

    /**
     * If the result is just the "probably cake" disk we seeded, GrabCut found no edge there (a
     * tap on the table, say): nearly all of the disk kept and almost nothing outside it.
     */
    private const val ECHO_INSIDE = 0.9
    private const val ECHO_OUTSIDE = 0.05

    /** Band along the picture's edges taken as table, unless the tap is right there. */
    private const val EDGE_BAND = 0.03

    private val openCvReady: Boolean by lazy {
        OpenCVLoader.initLocal().also { if (!it) Log.w(TAG, "OpenCV failed to load") }
    }

    private fun echoesSeed(cake: BooleanArray, w: Int, h: Int, tx: Int, ty: Int, radius: Int): Boolean {
        var disk = 0
        var diskKept = 0
        var kept = 0
        val r2 = radius.toLong() * radius
        for (i in cake.indices) {
            val dx = i % w - tx
            val dy = i / w - ty
            val inDisk = dx.toLong() * dx + dy.toLong() * dy <= r2
            if (inDisk) disk++
            if (cake[i]) {
                kept++
                if (inDisk) diskKept++
            }
        }
        if (disk == 0 || kept == 0) return false
        return diskKept >= ECHO_INSIDE * disk && kept - diskKept <= ECHO_OUTSIDE * kept
    }

    /**
     * What GrabCut kept around one tap, on the small working copy of the picture ([scale] times
     * the frame's size), with the tap in those pixels.
     */
    class Segment(val mask: BooleanArray, val width: Int, val height: Int, val scale: Double, val tapX: Int, val tapY: Int)

    /**
     * The outline of the cake under ([tapX], [tapY]), both in [frame] pixels, or null when there
     * is no clear cake there. Takes a few hundred milliseconds: call it off the main thread.
     */
    fun snap(frame: Bitmap, tapX: Float, tapY: Float): SnapFit.Result? = segment(frame, tapX, tapY)?.let(::fit)

    /** [segment]'s outline in frame pixels, or null if it isn't cake-shaped. */
    fun fit(segment: Segment): SnapFit.Result? =
        SnapFit.fromMask(segment.mask, segment.width, segment.height, segment.tapX, segment.tapY)?.scaled(1.0 / segment.scale)

    /** GrabCut around one tap; null when it found nothing there. Off the main thread. */
    fun segment(frame: Bitmap, tapX: Float, tapY: Float): Segment? {
        if (!openCvReady || frame.width < 2 || frame.height < 2) return null
        val scale = min(1.0, WORK_SIDE.toDouble() / max(frame.width, frame.height))
        val w = max(2, (frame.width * scale).roundToInt())
        val h = max(2, (frame.height * scale).roundToInt())
        val tx = (tapX * scale).roundToInt().coerceIn(0, w - 1)
        val ty = (tapY * scale).roundToInt().coerceIn(0, h - 1)

        val source = if (frame.config == Bitmap.Config.ARGB_8888) frame else frame.copy(Bitmap.Config.ARGB_8888, false)
        val small = Bitmap.createScaledBitmap(source, w, h, true)
        val rgba = Mat()
        val rgb = Mat()
        val mask = Mat(h, w, CvType.CV_8UC1, Scalar(Imgproc.GC_PR_BGD.toDouble()))
        val bgdModel = Mat()
        val fgdModel = Mat()
        try {
            Utils.bitmapToMat(small, rgba)
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)

            val side = min(w, h)
            val band = max(1, (EDGE_BAND * side).roundToInt())
            val likely = (LIKELY_RADIUS * side).roundToInt()
            val sure = max(2, (SURE_RADIUS * side).roundToInt())
            val bg = Scalar(Imgproc.GC_BGD.toDouble())
            Imgproc.rectangle(mask, Point(0.0, 0.0), Point(w - 1.0, band - 1.0), bg, -1)
            Imgproc.rectangle(mask, Point(0.0, h - band.toDouble()), Point(w - 1.0, h - 1.0), bg, -1)
            Imgproc.rectangle(mask, Point(0.0, 0.0), Point(band - 1.0, h - 1.0), bg, -1)
            Imgproc.rectangle(mask, Point(w - band.toDouble(), 0.0), Point(w - 1.0, h - 1.0), bg, -1)
            val tap = Point(tx.toDouble(), ty.toDouble())
            // Drawn after the edges, so a tap near an edge still owns its surroundings.
            Imgproc.circle(mask, tap, likely, Scalar(Imgproc.GC_PR_FGD.toDouble()), -1)
            Imgproc.circle(mask, tap, sure, Scalar(Imgproc.GC_FGD.toDouble()), -1)

            Imgproc.grabCut(rgb, mask, Rect(), bgdModel, fgdModel, ITERATIONS, Imgproc.GC_INIT_WITH_MASK)

            val labels = ByteArray(w * h)
            mask.get(0, 0, labels)
            // GC_FGD (1) and GC_PR_FGD (3) are the odd labels.
            val cake = BooleanArray(w * h) { labels[it].toInt() and 1 == 1 }
            if (echoesSeed(cake, w, h, tx, ty, likely)) return null
            return Segment(cake, w, h, scale, tx, ty)
        } catch (e: Exception) {
            Log.w(TAG, "Snap failed", e)
            return null
        } finally {
            rgba.release()
            rgb.release()
            mask.release()
            bgdModel.release()
            fgdModel.release()
            if (small !== source) small.recycle()
            if (source !== frame) source.recycle()
        }
    }
}
