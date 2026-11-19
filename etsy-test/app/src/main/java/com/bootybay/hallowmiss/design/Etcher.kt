package com.bootybay.hallowmiss.design

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/**
 * Turns an old engraving / woodcut scan into a clean 1-bit ink mask:
 * grayscale → contrast stretch → adaptive threshold (handles yellowed, foxed paper) → crop to the ink.
 */
object Etcher {
    class Result(val mask: BooleanArray, val w: Int, val h: Int, val ink: Float)

    fun etch(src: Bitmap, maxSide: Int = 720): Result? {
        val s = min(1f, maxSide.toFloat() / max(src.width, src.height))
        val b = if (s < 1f) Bitmap.createScaledBitmap(src, max(1, (src.width * s).toInt()), max(1, (src.height * s).toInt()), true) else src
        val w = b.width
        val h = b.height
        if (w < 80 || h < 80) return null
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        if (b !== src) b.recycle()

        val g = IntArray(w * h)
        val hist = IntArray(256)
        for (i in px.indices) {
            val p = px[i]
            var l = ((p shr 16 and 255) * 299 + (p shr 8 and 255) * 587 + (p and 255) * 114) / 1000
            if ((p ushr 24) < 128) l = 255
            g[i] = l
            hist[l]++
        }
        val lo = percentile(hist, w * h, 0.02)
        val hi = percentile(hist, w * h, 0.98)
        val range = max(1, hi - lo)
        for (i in g.indices) g[i] = ((g[i] - lo) * 255 / range).coerceIn(0, 255)

        // Integral image for a fast local mean.
        val iw = w + 1
        val integ = LongArray(iw * (h + 1))
        for (y in 0 until h) {
            var row = 0L
            for (x in 0 until w) {
                row += g[y * w + x]
                integ[(y + 1) * iw + x + 1] = integ[y * iw + x + 1] + row
            }
        }
        val r = max(6, max(w, h) / 24)
        val mask = BooleanArray(w * h)
        val rowInk = IntArray(h)
        val colInk = IntArray(w)
        var total = 0
        for (y in 0 until h) {
            val y0 = max(0, y - r)
            val y1 = min(h - 1, y + r)
            for (x in 0 until w) {
                val x0 = max(0, x - r)
                val x1 = min(w - 1, x + r)
                val area = (x1 - x0 + 1) * (y1 - y0 + 1)
                val sum = integ[(y1 + 1) * iw + x1 + 1] - integ[y0 * iw + x1 + 1] - integ[(y1 + 1) * iw + x0] + integ[y0 * iw + x0]
                val mean = sum / area
                val v = g[y * w + x]
                if (v < 60 || (v < mean * 0.85 && v < 200)) {
                    mask[y * w + x] = true
                    rowInk[y]++; colInk[x]++; total++
                }
            }
        }
        if (total == 0) return null

        // Crop to where the ink is (ignore the outer 0.5% of stray marks on each side).
        val cut = max(1, total / 200)
        val top = edge(rowInk, cut, false)
        val bottom = edge(rowInk, cut, true)
        val left = edge(colInk, cut, false)
        val right = edge(colInk, cut, true)
        val cx0 = max(0, left - 3)
        val cy0 = max(0, top - 3)
        val cw = min(w - 1, right + 3) - cx0 + 1
        val ch = min(h - 1, bottom + 3) - cy0 + 1
        if (cw < 60 || ch < 60) return null
        val out = BooleanArray(cw * ch)
        var ink = 0
        for (y in 0 until ch) for (x in 0 until cw) {
            if (mask[(y + cy0) * w + x + cx0]) { out[y * cw + x] = true; ink++ }
        }
        val ratio = ink.toFloat() / (cw * ch)
        // Too little ink = blank/faint scan; too much = a photo or a dark painting, not an etching.
        if (ratio < 0.04f || ratio > 0.5f) return null
        return Result(out, cw, ch, ratio)
    }

    private fun percentile(hist: IntArray, n: Int, p: Double): Int {
        val target = (n * p).toLong()
        var acc = 0L
        for (i in 0..255) { acc += hist[i]; if (acc >= target) return i }
        return 255
    }

    private fun edge(counts: IntArray, cut: Int, fromEnd: Boolean): Int {
        var acc = 0
        val idx = if (fromEnd) counts.indices.reversed() else counts.indices
        for (i in idx) { acc += counts[i]; if (acc >= cut) return i }
        return if (fromEnd) counts.size - 1 else 0
    }
}
