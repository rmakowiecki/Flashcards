@file:Suppress("MagicNumber", "LoopWithTooManyJumpStatements")

package com.rossomak.flashcards.feature.debug.networkgraph

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** How far past the points' bounding box the enclosing super-triangle reaches, in box spans. */
private const val SUPER_TRIANGLE_SCALE = 20.0
private const val DEGENERATE_DETERMINANT = 1e-9
private const val DEGREES_TO_RADIANS = 0.017453292f

/**
 * PROTOTYPE — throwaway, see [NetworkGraphPrototypeScreen].
 *
 * Bowyer–Watson Delaunay triangulation, hand-written rather than pulled in as a dependency. Each
 * insertion scans every live triangle, so it is O(n²) in the worst case — a few hundred background
 * nodes take on the order of a millisecond, which is what lets [EdgeRule.LivingDelaunay] re-run it
 * every frame.
 */
internal object Delaunay {

    /** Vertex index triples into [xs]/[ys], three entries per triangle. */
    fun triangulate(xs: FloatArray, ys: FloatArray, count: Int): IntArray {
        if (count < 3) return IntArray(0)
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (index in 0 until count) {
            minX = min(minX, xs[index])
            minY = min(minY, ys[index])
            maxX = max(maxX, xs[index])
            maxY = max(maxY, ys[index])
        }
        val span = max(maxX - minX, maxY - minY).coerceAtLeast(1f).toDouble()
        val midX = (minX + maxX) / 2.0
        val midY = (minY + maxY) / 2.0

        // Doubles: the circumcircle test on near-collinear triples is where float precision breaks.
        val pointsX = DoubleArray(count + 3) { index -> if (index < count) xs[index].toDouble() else 0.0 }
        val pointsY = DoubleArray(count + 3) { index -> if (index < count) ys[index].toDouble() else 0.0 }
        pointsX[count] = midX - SUPER_TRIANGLE_SCALE * span
        pointsY[count] = midY - span
        pointsX[count + 1] = midX
        pointsY[count + 1] = midY + SUPER_TRIANGLE_SCALE * span
        pointsX[count + 2] = midX + SUPER_TRIANGLE_SCALE * span
        pointsY[count + 2] = midY - span

        val triangles = ArrayList<Triangle>()
        triangles += Triangle.of(count, count + 1, count + 2, pointsX, pointsY)
        val badTriangles = ArrayList<Triangle>()
        val edgeOccurrences = HashMap<Long, Int>()
        for (point in 0 until count) {
            val pointX = pointsX[point]
            val pointY = pointsY[point]
            badTriangles.clear()
            edgeOccurrences.clear()
            var index = 0
            while (index < triangles.size) {
                val triangle = triangles[index]
                if (triangle.circumcircleContains(pointX, pointY)) {
                    badTriangles += triangle
                    // Swap-remove: order does not matter and this keeps removal O(1).
                    triangles[index] = triangles[triangles.lastIndex]
                    triangles.removeAt(triangles.lastIndex)
                } else {
                    index++
                }
            }
            for (triangle in badTriangles) {
                edgeOccurrences.merge(edgeKey(triangle.first, triangle.second), 1, Int::plus)
                edgeOccurrences.merge(edgeKey(triangle.second, triangle.third), 1, Int::plus)
                edgeOccurrences.merge(edgeKey(triangle.third, triangle.first), 1, Int::plus)
            }
            // The cavity's boundary is every edge only one bad triangle had; each is re-fanned to the point.
            for ((key, occurrences) in edgeOccurrences) {
                if (occurrences == 1) triangles += Triangle.of(edgeStart(key), edgeEnd(key), point, pointsX, pointsY)
            }
        }

        val result = ArrayList<Int>(triangles.size * 3)
        for (triangle in triangles) {
            if (triangle.first >= count || triangle.second >= count || triangle.third >= count) continue
            result += triangle.first
            result += triangle.second
            result += triangle.third
        }
        return result.toIntArray()
    }

    private class Triangle(
        val first: Int,
        val second: Int,
        val third: Int,
        private val centerX: Double,
        private val centerY: Double,
        private val radiusSquared: Double,
    ) {
        fun circumcircleContains(x: Double, y: Double): Boolean {
            val deltaX = x - centerX
            val deltaY = y - centerY
            return deltaX * deltaX + deltaY * deltaY < radiusSquared
        }

        companion object {
            fun of(first: Int, second: Int, third: Int, xs: DoubleArray, ys: DoubleArray): Triangle {
                val ax = xs[first]
                val ay = ys[first]
                val bx = xs[second]
                val by = ys[second]
                val cx = xs[third]
                val cy = ys[third]
                val determinant = 2.0 * (ax * (by - cy) + bx * (cy - ay) + cx * (ay - by))
                // A collinear triple has no circumcircle; treating it as infinite gets it replaced by
                // the next insertion, and the min-angle filter drops any that survive to the end.
                if (abs(determinant) < DEGENERATE_DETERMINANT) {
                    return Triangle(first, second, third, ax, ay, Double.MAX_VALUE)
                }
                val aSquared = ax * ax + ay * ay
                val bSquared = bx * bx + by * by
                val cSquared = cx * cx + cy * cy
                val centerX = (aSquared * (by - cy) + bSquared * (cy - ay) + cSquared * (ay - by)) / determinant
                val centerY = (aSquared * (cx - bx) + bSquared * (ax - cx) + cSquared * (bx - ax)) / determinant
                val deltaX = ax - centerX
                val deltaY = ay - centerY
                return Triangle(first, second, third, centerX, centerY, deltaX * deltaX + deltaY * deltaY)
            }
        }
    }
}

/**
 * The unique undirected edges of [triangles], cleaned up: triangles with a corner under
 * [minAngleDegrees] contribute nothing (slivers along the hull and across sparse gaps), edges longer
 * than [maxLength] are dropped, and of the rest each survives with [keepChance], decided by a stable
 * per-pair hash so the same pair keeps its verdict frame after frame.
 *
 * Returned as packed [edgeKey]s, sorted.
 */
internal fun meshEdgeKeys(
    triangles: IntArray,
    xs: FloatArray,
    ys: FloatArray,
    minAngleDegrees: Float,
    maxLength: Float,
    keepChance: Float,
    seed: Int,
): LongArray {
    val maxCosine = cos(minAngleDegrees * DEGREES_TO_RADIANS)
    val maxLengthSquared = maxLength * maxLength
    val candidates = LongArray(triangles.size)
    var candidateCount = 0
    for (base in triangles.indices step 3) {
        val first = triangles[base]
        val second = triangles[base + 1]
        val third = triangles[base + 2]
        if (smallestAngleCosine(first, second, third, xs, ys) > maxCosine) continue
        candidates[candidateCount++] = edgeKey(first, second)
        candidates[candidateCount++] = edgeKey(second, third)
        candidates[candidateCount++] = edgeKey(third, first)
    }
    candidates.sort(fromIndex = 0, toIndex = candidateCount)

    val result = LongArray(candidateCount)
    var resultCount = 0
    var previous = -1L
    for (index in 0 until candidateCount) {
        val key = candidates[index]
        if (key == previous) continue
        previous = key
        val start = edgeStart(key)
        val end = edgeEnd(key)
        val deltaX = xs[end] - xs[start]
        val deltaY = ys[end] - ys[start]
        if (deltaX * deltaX + deltaY * deltaY > maxLengthSquared) continue
        if (pairRandom(start, end, seed) >= keepChance) continue
        result[resultCount++] = key
    }
    return result.copyOf(resultCount)
}

/** Cosine of the triangle's smallest corner — the largest of its three corner cosines. */
private fun smallestAngleCosine(first: Int, second: Int, third: Int, xs: FloatArray, ys: FloatArray): Float {
    val sideA = distanceSquared(second, third, xs, ys)
    val sideB = distanceSquared(first, third, xs, ys)
    val sideC = distanceSquared(first, second, xs, ys)
    if (sideA <= 0f || sideB <= 0f || sideC <= 0f) return 1f
    val cosineA = (sideB + sideC - sideA) / (2f * sqrt(sideB * sideC))
    val cosineB = (sideA + sideC - sideB) / (2f * sqrt(sideA * sideC))
    val cosineC = (sideA + sideB - sideC) / (2f * sqrt(sideA * sideB))
    return max(cosineA, max(cosineB, cosineC))
}

private fun distanceSquared(first: Int, second: Int, xs: FloatArray, ys: FloatArray): Float {
    val deltaX = xs[first] - xs[second]
    val deltaY = ys[first] - ys[second]
    return deltaX * deltaX + deltaY * deltaY
}

/** An undirected edge packed into one Long, lower index in the high half. */
internal fun edgeKey(first: Int, second: Int): Long = (min(first, second).toLong() shl 32) or max(first, second).toLong()

internal fun edgeStart(key: Long): Int = (key ushr 32).toInt()

internal fun edgeEnd(key: Long): Int = (key and 0xFFFFFFFFL).toInt()

/** A stable random in 0..1 for an unordered pair, so a pair's verdict never flickers between frames. */
internal fun pairRandom(first: Int, second: Int, seed: Int): Float {
    var hash = min(first, second) * 73_856_093 xor max(first, second) * 19_349_663 xor seed * 83_492_791
    hash = hash xor (hash ushr 13)
    hash *= 0x5BD1E995
    hash = hash xor (hash ushr 15)
    return (hash ushr 8) / 16_777_216f
}
