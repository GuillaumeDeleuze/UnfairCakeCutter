package com.unfaircake.cutter.snap

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.unfaircake.cutter.domain.CakeShape
import com.unfaircake.cutter.domain.SnapFit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/** GrabCut + fit on painted scenes: a table, a cloth, a cake with its decorations. */
@RunWith(AndroidJUnit4::class)
class CakeSnapperTest {

    private val w = 1080
    private val h = 1260
    private val random = Random(7)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun woodTable(canvas: Canvas) {
        canvas.drawColor(Color.rgb(122, 78, 48))
        paint.color = Color.rgb(104, 64, 38)
        var x = 0f
        while (x < w) {
            canvas.drawRect(x, 0f, x + 26f, h.toFloat(), paint)
            x += 70f
        }
    }

    private fun gingham(canvas: Canvas) {
        canvas.drawColor(Color.rgb(250, 214, 234))
        paint.color = Color.argb(150, 255, 110, 180)
        for (i in 0 until w / 60 + 1) canvas.drawRect(i * 60f, 0f, i * 60f + 30f, h.toFloat(), paint)
        for (j in 0 until h / 60 + 1) canvas.drawRect(0f, j * 60f, w.toFloat(), j * 60f + 30f, paint)
    }

    private fun sprinkles(canvas: Canvas, area: RectF, n: Int) {
        val colors = intArrayOf(Color.rgb(92, 225, 230), Color.rgb(179, 136, 255), Color.YELLOW, Color.WHITE)
        repeat(n) {
            paint.color = colors[random.nextInt(colors.size)]
            val cx = area.left + random.nextFloat() * area.width()
            val cy = area.top + random.nextFloat() * area.height()
            canvas.drawCircle(cx, cy, 6f, paint)
        }
    }

    private fun scene(draw: (Canvas) -> Unit): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { draw(Canvas(it)) }

    @Test
    fun roundCakeOnAPlate() {
        val cake = RectF(240f, 420f, 840f, 880f)
        val frame = scene { c ->
            woodTable(c)
            paint.color = Color.rgb(244, 236, 239)
            c.drawOval(RectF(170f, 360f, 910f, 950f), paint)
            paint.color = Color.rgb(255, 140, 192)
            c.drawOval(cake, paint)
            sprinkles(c, RectF(360f, 520f, 720f, 780f), 60)
        }
        val r = CakeSnapper.snap(frame, 540f, 650f)
        assertNotNull(r); r!!
        assertTrue("round", r.round)
        assertEquals(540.0, r.cx, 30.0)
        assertEquals(650.0, r.cy, 30.0)
        assertEquals(600.0, r.width, 60.0)
        assertEquals(460.0, r.height, 60.0)
    }

    @Test
    fun lasagnaTrayOnGingham() {
        val frame = scene { c ->
            gingham(c)
            c.save()
            c.rotate(-8f, 540f, 630f)
            paint.color = Color.rgb(201, 92, 40)
            c.drawRoundRect(RectF(200f, 400f, 880f, 860f), 24f, 24f, paint)
            paint.color = Color.rgb(236, 180, 70)
            sprinkles(c, RectF(260f, 460f, 820f, 800f), 40)
            c.restore()
        }
        val r = CakeSnapper.snap(frame, 540f, 630f)
        assertNotNull(r); r!!
        assertFalse("tray", r.round)
        assertEquals(CakeShape.TRAY_GRID, SnapFit.shapeFor(r, CakeShape.ROUND))
        assertEquals(-8.0, r.rotationDeg, 4.0)
        assertEquals(680.0, r.width, 60.0)
    }

    @Test
    fun rollCake() {
        val frame = scene { c ->
            woodTable(c)
            paint.color = Color.rgb(90, 40, 22)
            c.drawRoundRect(RectF(120f, 560f, 960f, 760f), 60f, 60f, paint)
        }
        val r = CakeSnapper.snap(frame, 540f, 660f)
        assertNotNull(r); r!!
        assertEquals(CakeShape.LOG, SnapFit.shapeFor(r, CakeShape.ROUND))
    }

    @Test
    fun tapOnTheTableIsNotACake() {
        val frame = scene { c ->
            woodTable(c)
            paint.color = Color.rgb(244, 236, 239)
            c.drawOval(RectF(170f, 360f, 910f, 950f), paint)
            paint.color = Color.rgb(255, 140, 192)
            c.drawOval(RectF(240f, 420f, 840f, 880f), paint)
        }
        assertNull(CakeSnapper.snap(frame, 120f, 150f))
    }

    @Test
    fun emptyTableIsNotACake() {
        val frame = scene { c -> c.drawColor(Color.rgb(122, 78, 48)) }
        assertNull(CakeSnapper.snap(frame, 540f, 630f))
    }
}
