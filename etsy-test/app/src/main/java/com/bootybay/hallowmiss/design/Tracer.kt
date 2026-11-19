package com.bootybay.hallowmiss.design

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Vectoriser: traces the outlines of a 1-bit mask into closed polygons (outer shapes clockwise,
 * holes counter-clockwise, so even-odd filling renders them correctly), then simplifies them
 * with Ramer–Douglas–Peucker. The composer draws them as smooth quadratic curves, so the
 * artwork scales to print size without pixelation.
 */
object Tracer {
    private const val E = 0
    private const val S = 1
    private const val W = 2
    private const val N = 3

    fun trace(mask: BooleanArray, w: Int, h: Int, minPerimeter: Int = 14, eps: Float = 0.9f): List<FloatArray> {
        val vw = w + 1
        val n = vw * (h + 1)
        val outA = ByteArray(n)
        val outB = ByteArray(n)
        fun ink(x: Int, y: Int) = x in 0 until w && y in 0 until h && mask[y * w + x]
        fun add(v: Int, d: Int) { if (outA[v].toInt() == 0) outA[v] = (d + 1).toByte() else outB[v] = (d + 1).toByte() }

        for (y in 0 until h) for (x in 0 until w) {
            if (!mask[y * w + x]) continue
            if (!ink(x, y - 1)) add(y * vw + x, E)
            if (!ink(x + 1, y)) add(y * vw + x + 1, S)
            if (!ink(x, y + 1)) add((y + 1) * vw + x + 1, W)
            if (!ink(x - 1, y)) add((y + 1) * vw + x, N)
        }

        // Take an outgoing edge from v, preferring right turn, then straight, then left.
        fun take(v: Int, d: Int): Int {
            val order = intArrayOf((d + 1) % 4, d, (d + 3) % 4)
            for (want in order) {
                if (outA[v].toInt() == want + 1) { outA[v] = outB[v]; outB[v] = 0; return want }
                if (outB[v].toInt() == want + 1) { outB[v] = 0; return want }
            }
            return -1
        }

        val loops = ArrayList<FloatArray>()
        var xs = IntArray(256)
        var ys = IntArray(256)

        for (pass in 0..1) {
            for (start in 0 until n) {
                while (outA[start].toInt() != 0 && (pass == 1 || outB[start].toInt() == 0)) {
                    var d = outA[start] - 1
                    outA[start] = outB[start]; outB[start] = 0
                    var count = 0
                    var steps = 0
                    xs[0] = start % vw; ys[0] = start / vw; count = 1
                    var v = start
                    while (true) {
                        v = when (d) { E -> v + 1; S -> v + vw; W -> v - 1; else -> v - vw }
                        steps++
                        if (v == start) break
                        val nd = take(v, d)
                        if (nd < 0) break
                        if (nd != d) {
                            if (count == xs.size) { xs = xs.copyOf(count * 2); ys = ys.copyOf(count * 2) }
                            xs[count] = v % vw; ys[count] = v / vw; count++
                        }
                        d = nd
                    }
                    if (steps >= minPerimeter && count >= 3) loops.add(simplify(xs, ys, count, eps))
                }
            }
        }
        return loops
    }

    private fun simplify(xs: IntArray, ys: IntArray, n: Int, eps: Float): FloatArray {
        if (n < 5) return FloatArray(n * 2) { i -> if (i % 2 == 0) xs[i / 2].toFloat() else ys[i / 2].toFloat() }
        val keep = BooleanArray(n + 1)
        keep[0] = true; keep[n] = true
        val stack = IntArray(2 * (n + 2))
        var sp = 0
        stack[sp++] = 0; stack[sp++] = n
        while (sp > 0) {
            val b = stack[--sp]
            val a = stack[--sp]
            if (b - a < 2) continue
            val ax = xs[a % n].toFloat(); val ay = ys[a % n].toFloat()
            val bx = xs[b % n].toFloat(); val by = ys[b % n].toFloat()
            var best = -1f
            var bi = -1
            for (i in a + 1 until b) {
                val d = dist(xs[i].toFloat(), ys[i].toFloat(), ax, ay, bx, by)
                if (d > best) { best = d; bi = i }
            }
            if (best > eps && bi > 0) {
                keep[bi] = true
                if (sp + 4 > stack.size) break
                stack[sp++] = a; stack[sp++] = bi
                stack[sp++] = bi; stack[sp++] = b
            }
        }
        var m = 0
        for (i in 0 until n) if (keep[i]) m++
        val out = FloatArray(max(m, 3) * 2)
        var j = 0
        for (i in 0 until n) if (keep[i]) { out[j++] = xs[i].toFloat(); out[j++] = ys[i].toFloat() }
        if (m < 3) { // degenerate: keep the raw corners
            return FloatArray(n * 2) { i -> if (i % 2 == 0) xs[i / 2].toFloat() else ys[i / 2].toFloat() }
        }
        return out
    }

    private fun dist(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 < 1e-6f) return hypot(px - ax, py - ay)
        return abs(dy * px - dx * py + bx * ay - by * ax) / sqrt(len2)
    }
}
