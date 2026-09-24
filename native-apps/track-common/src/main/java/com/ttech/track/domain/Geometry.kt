package com.ttech.track.domain

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/** グラフ・地図の下絵用に、点を間引く道具(記録そのものは間引かない) */
object Geometry {
    /**
     * 折れ線を、形をほとんど変えずに間引く(Ramer–Douglas–Peucker)。
     * [toleranceM] より元の線から外れる点だけを残す。一覧のサムネイルなど、細部が要らない描画に使う。
     */
    fun simplify(points: List<LatLon>, toleranceM: Double): List<LatLon> {
        if (points.size <= 2) return points
        val lat0 = points.first().lat
        val mPerDegLat = GeoMath.EARTH_RADIUS_M * Math.PI / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(lat0))
        val xs = DoubleArray(points.size) { (points[it].lon - points[0].lon) * mPerDegLon }
        val ys = DoubleArray(points.size) { (points[it].lat - points[0].lat) * mPerDegLat }

        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true
        val stack = ArrayDeque<IntArray>()
        stack.addLast(intArrayOf(0, points.lastIndex))
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            var maxDist = 0.0
            var index = -1
            for (i in a + 1 until b) {
                val d = perpendicularDistance(xs[i], ys[i], xs[a], ys[a], xs[b], ys[b])
                if (d > maxDist) {
                    maxDist = d
                    index = i
                }
            }
            if (index != -1 && maxDist > toleranceM) {
                keep[index] = true
                stack.addLast(intArrayOf(a, index))
                stack.addLast(intArrayOf(index, b))
            }
        }
        return points.filterIndexed { i, _ -> keep[i] }
    }

    private fun perpendicularDistance(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax
        val dy = by - ay
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1e-9) return sqrt((px - ax) * (px - ax) + (py - ay) * (py - ay))
        return abs(dy * px - dx * py + bx * ay - by * ax) / len
    }

    /**
     * グラフ用に、系列を最大 [maxPoints] 点に減らす(Largest-Triangle-Three-Buckets)。
     * 先頭・末尾を必ず残し、山や谷(最高速度など)が消えにくい。返すのは元の系列の添字。
     */
    fun downsampleIndices(x: DoubleArray, y: DoubleArray, maxPoints: Int): List<Int> {
        val n = x.size
        require(y.size == n)
        if (maxPoints >= n || maxPoints < 3) return (0 until n).toList()
        val result = ArrayList<Int>(maxPoints)
        result.add(0)
        val bucketSize = (n - 2).toDouble() / (maxPoints - 2)
        var a = 0
        for (i in 0 until maxPoints - 2) {
            val rangeStart = (i * bucketSize).toInt() + 1
            val rangeEnd = ((i + 1) * bucketSize).toInt() + 1
            val nextStart = rangeEnd
            val nextEnd = (((i + 2) * bucketSize).toInt() + 1).coerceAtMost(n)
            var avgX = 0.0
            var avgY = 0.0
            val nextCount = (nextEnd - nextStart).coerceAtLeast(1)
            for (j in nextStart until nextEnd.coerceAtMost(n)) {
                avgX += x[j]
                avgY += y[j]
            }
            avgX /= nextCount
            avgY /= nextCount
            var maxArea = -1.0
            var chosen = rangeStart
            for (j in rangeStart until rangeEnd.coerceAtMost(n - 1)) {
                val area = abs((x[a] - avgX) * (y[j] - y[a]) - (x[a] - x[j]) * (avgY - y[a]))
                if (area > maxArea) {
                    maxArea = area
                    chosen = j
                }
            }
            result.add(chosen)
            a = chosen
        }
        result.add(n - 1)
        return result
    }
}
