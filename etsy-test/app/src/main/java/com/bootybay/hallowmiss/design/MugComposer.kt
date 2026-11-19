package com.bootybay.hallowmiss.design

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import com.bootybay.hallowmiss.net.Commons
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class Motif(val loops: List<FloatArray>, val w: Int, val h: Int, val source: Commons.Img)

/**
 * Lays out a Printful 11oz mug wrap (2700×1050 px, 300 dpi), printed both sides:
 *   left half  – the Halloween etching, dusted with snow (holes knocked out of the ink), holly on the frame
 *   right half – the Christmas etching in a blood/pine ink, with bats circling the frame
 *   each half  – the phrase underneath
 * Everything is vector, so the same geometry is written to an SVG.
 */
object MugComposer {
    const val W = 2700
    const val H = 1050

    class Spec(val phrase: String, val left: Motif, val right: Motif, val palette: Ideas.Palette, val seed: Long)
    class Out(val bitmap: Bitmap, val svg: String)

    fun compose(spec: Spec): Out {
        val rnd = Random(spec.seed)
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val svg = StringBuilder(1 shl 20)
        val defs = StringBuilder()
        val body = StringBuilder(1 shl 20)

        for (side in 0..1) {
            val motif = if (side == 0) spec.left else spec.right
            val ink = if (side == 0) spec.palette.inkA else spec.palette.inkB
            val fx = side * 1350f + 140f
            val fy = 70f
            val fw = 1070f
            val fh = 700f
            val pad = 36f
            val loops = place(motif, fx + pad, fy + pad, fw - 2 * pad, fh - 2 * pad)
            val snow = if (side == 0) snow(rnd, fx + pad, fy + pad, fw - 2 * pad, fh - 2 * pad) else emptyList()
            drawMotif(c, body, defs, loops, ink, snow, "snow$side")
            frame(c, body, fx, fy, fw, fh, spec.palette.inkA)
            if (side == 0) {
                sprig(c, body, fx + 8f, fy + 8f, 60f, -3 * PI / 4, spec.palette)
                sprig(c, body, fx + fw - 8f, fy + 8f, 60f, -PI / 4, spec.palette)
            } else {
                bats(c, body, rnd, fx, fy, fw, spec.palette.inkA)
            }
            text(c, body, spec.phrase, fx + fw / 2f, 800f, fw, 210f, spec.palette.accent)
        }

        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$W\" height=\"$H\" viewBox=\"0 0 $W $H\">")
        if (defs.isNotEmpty()) svg.append("<defs>").append(defs).append("</defs>")
        svg.append(body).append("</svg>")
        return Out(bmp, svg.toString())
    }

    // ---------- geometry ----------
    private fun place(m: Motif, x: Float, y: Float, w: Float, h: Float): List<FloatArray> {
        val s = min(w / m.w, h / m.h)
        val ox = x + (w - m.w * s) / 2f
        val oy = y + (h - m.h * s) / 2f
        return m.loops.map { src -> FloatArray(src.size) { i -> if (i % 2 == 0) src[i] * s + ox else src[i] * s + oy } }
    }

    private fun snow(rnd: Random, x: Float, y: Float, w: Float, h: Float): List<FloatArray> =
        List(90) { floatArrayOf(x + rnd.nextFloat() * w, y + rnd.nextFloat() * h, 3f + rnd.nextFloat() * rnd.nextFloat() * 12f) }

    private fun f(v: Float): String = (Math.round(v * 10f) / 10f).toString()
    private fun hex(color: Int): String = String.format("#%06X", color and 0xFFFFFF)
    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }

    /** Smooth closed curves through the midpoints of each polygon edge. */
    private fun smoothPath(loops: List<FloatArray>, d: StringBuilder?): Path {
        val p = Path()
        p.fillType = Path.FillType.EVEN_ODD
        for (pts in loops) {
            val n = pts.size / 2
            if (n < 3) continue
            fun x(i: Int) = pts[(i % n) * 2]
            fun y(i: Int) = pts[(i % n) * 2 + 1]
            val sx = (x(0) + x(1)) / 2f
            val sy = (y(0) + y(1)) / 2f
            p.moveTo(sx, sy)
            d?.append('M')?.append(f(sx))?.append(' ')?.append(f(sy))
            for (i in 1..n) {
                val mx = (x(i) + x(i + 1)) / 2f
                val my = (y(i) + y(i + 1)) / 2f
                p.quadTo(x(i), y(i), mx, my)
                d?.append('Q')?.append(f(x(i)))?.append(' ')?.append(f(y(i)))?.append(' ')?.append(f(mx))?.append(' ')?.append(f(my))
            }
            p.close()
            d?.append('Z')
        }
        return p
    }

    private fun drawMotif(c: Canvas, body: StringBuilder, defs: StringBuilder, loops: List<FloatArray>, ink: Int, snow: List<FloatArray>, id: String) {
        val d = StringBuilder(loops.size * 60)
        val path = smoothPath(loops, d)
        if (snow.isEmpty()) {
            c.drawPath(path, fill(ink))
            body.append("<path fill=\"${hex(ink)}\" fill-rule=\"evenodd\" d=\"").append(d).append("\"/>")
            return
        }
        val layer = c.saveLayer(null, null)
        c.drawPath(path, fill(ink))
        val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        for (s in snow) c.drawCircle(s[0], s[1], s[2], clear)
        c.restoreToCount(layer)

        defs.append("<mask id=\"$id\"><rect width=\"$W\" height=\"$H\" fill=\"white\"/>")
        for (s in snow) defs.append("<circle cx=\"${f(s[0])}\" cy=\"${f(s[1])}\" r=\"${f(s[2])}\" fill=\"black\"/>")
        defs.append("</mask>")
        body.append("<path fill=\"${hex(ink)}\" fill-rule=\"evenodd\" mask=\"url(#$id)\" d=\"").append(d).append("\"/>")
    }

    /** Engraved-plate double rule. */
    private fun frame(c: Canvas, body: StringBuilder, x: Float, y: Float, w: Float, h: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE }
        p.strokeWidth = 7f
        c.drawRect(x, y, x + w, y + h, p)
        p.strokeWidth = 2.5f
        c.drawRect(x + 14f, y + 14f, x + w - 14f, y + h - 14f, p)
        body.append("<rect x=\"${f(x)}\" y=\"${f(y)}\" width=\"${f(w)}\" height=\"${f(h)}\" fill=\"none\" stroke=\"${hex(color)}\" stroke-width=\"7\"/>")
        body.append("<rect x=\"${f(x + 14)}\" y=\"${f(y + 14)}\" width=\"${f(w - 28)}\" height=\"${f(h - 28)}\" fill=\"none\" stroke=\"${hex(color)}\" stroke-width=\"2.5\"/>")
    }

    private fun polygon(c: Canvas, body: StringBuilder, pts: FloatArray, color: Int) {
        val p = Path()
        val d = StringBuilder()
        p.moveTo(pts[0], pts[1]); d.append("M${f(pts[0])} ${f(pts[1])}")
        var i = 2
        while (i < pts.size) { p.lineTo(pts[i], pts[i + 1]); d.append("L${f(pts[i])} ${f(pts[i + 1])}"); i += 2 }
        p.close(); d.append('Z')
        c.drawPath(p, fill(color))
        body.append("<path fill=\"${hex(color)}\" d=\"").append(d).append("\"/>")
    }

    private fun circle(c: Canvas, body: StringBuilder, x: Float, y: Float, r: Float, color: Int) {
        c.drawCircle(x, y, r, fill(color))
        body.append("<circle cx=\"${f(x)}\" cy=\"${f(y)}\" r=\"${f(r)}\" fill=\"${hex(color)}\"/>")
    }

    private fun transform(local: List<Float>, cx: Float, cy: Float, scale: Float, angle: Double): FloatArray {
        val ca = cos(angle).toFloat()
        val sa = sin(angle).toFloat()
        val out = FloatArray(local.size)
        var i = 0
        while (i < local.size) {
            val x = local[i] * scale
            val y = local[i + 1] * scale
            out[i] = cx + x * ca - y * sa
            out[i + 1] = cy + x * sa + y * ca
            i += 2
        }
        return out
    }

    /** Spiky holly leaf along +x, length 1. */
    private fun leaf(): List<Float> {
        val pts = ArrayList<Float>()
        val steps = 6
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val w = sin(PI * t).toFloat() * 0.3f * (if (i in 1 until steps && i % 2 == 1) 1.4f else 1f)
            pts.add(t); pts.add(-w)
        }
        for (i in steps - 1 downTo 1) {
            val t = i / steps.toFloat()
            val w = sin(PI * t).toFloat() * 0.3f * (if (i % 2 == 1) 1.4f else 1f)
            pts.add(t); pts.add(w)
        }
        return pts
    }

    private fun sprig(c: Canvas, body: StringBuilder, x: Float, y: Float, size: Float, angle: Double, pal: Ideas.Palette) {
        val green = 0xFF1E5631.toInt()
        val l = leaf()
        polygon(c, body, transform(l, x, y, size * 1.6f, angle - 0.55), green)
        polygon(c, body, transform(l, x, y, size * 1.6f, angle + 0.55), green)
        val berry = if (pal.accent == 0xFF111111.toInt()) 0xFFB3121F.toInt() else pal.accent
        val bx = x + cos(angle).toFloat() * size * 0.15f
        val by = y + sin(angle).toFloat() * size * 0.15f
        circle(c, body, bx, by, size * 0.16f, berry)
        circle(c, body, bx + size * 0.22f, by + size * 0.12f, size * 0.14f, berry)
        circle(c, body, bx - size * 0.12f, by + size * 0.22f, size * 0.13f, berry)
    }

    private val BAT_HALF = floatArrayOf(
        0f, -0.10f, 0.06f, -0.30f, 0.11f, -0.08f, 0.35f, -0.24f, 0.70f, -0.30f, 1.00f, -0.08f,
        0.84f, 0.02f, 0.70f, 0.12f, 0.55f, 0.03f, 0.40f, 0.16f, 0.25f, 0.06f, 0.10f, 0.20f, 0f, 0.30f,
    )

    private fun batShape(): List<Float> {
        val pts = ArrayList<Float>()
        var i = 0
        while (i < BAT_HALF.size) { pts.add(BAT_HALF[i]); pts.add(BAT_HALF[i + 1]); i += 2 }
        i = BAT_HALF.size - 4
        while (i >= 2) { pts.add(-BAT_HALF[i]); pts.add(BAT_HALF[i + 1]); i -= 2 }
        return pts
    }

    private fun bats(c: Canvas, body: StringBuilder, rnd: Random, fx: Float, fy: Float, fw: Float, color: Int) {
        val shape = batShape()
        val spots = listOf(fx - 40f to fy + 40f, fx + 90f to fy - 30f, fx + fw - 120f to fy - 28f, fx + fw + 30f to fy + 60f, fx + fw - 20f to fy + 170f)
        for ((x, y) in spots) {
            val s = 34f + rnd.nextFloat() * 26f
            polygon(c, body, transform(shape, x, y, s, (rnd.nextFloat() - 0.5f) * 0.6), color)
        }
    }

    private fun text(c: Canvas, body: StringBuilder, phrase: String, cx: Float, top: Float, maxW: Float, maxH: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.04f
        }
        val words = phrase.split(" ")
        var lines = listOf(phrase)
        var size = 150f
        while (size > 40f) {
            p.textSize = size
            lines = if (p.measureText(phrase) <= maxW || words.size < 3) listOf(phrase) else {
                var best = 1
                var bestDiff = Float.MAX_VALUE
                for (k in 1 until words.size) {
                    val diff = kotlin.math.abs(p.measureText(words.take(k).joinToString(" ")) - p.measureText(words.drop(k).joinToString(" ")))
                    if (diff < bestDiff) { bestDiff = diff; best = k }
                }
                listOf(words.take(best).joinToString(" "), words.drop(best).joinToString(" "))
            }
            val widest = lines.maxOf { p.measureText(it) }
            if (widest <= maxW && lines.size * size * 1.1f <= maxH) break
            size -= 6f
        }
        val lineH = size * 1.08f
        val blockTop = top + (maxH - lineH * lines.size) / 2f
        lines.forEachIndexed { i, line ->
            val baseline = blockTop + lineH * i + size * 0.82f
            c.drawText(line, cx, baseline, p)
            val safe = line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            body.append("<text x=\"${f(cx)}\" y=\"${f(baseline)}\" font-family=\"Georgia, 'Times New Roman', serif\" font-weight=\"bold\" ")
                .append("font-size=\"${f(size)}\" letter-spacing=\"${f(size * 0.04f)}\" text-anchor=\"middle\" fill=\"${hex(color)}\">")
                .append(safe).append("</text>")
        }
    }
}
