package com.solumetals.metrovision

import com.google.ar.core.Pose
import kotlin.math.abs
import kotlin.math.sqrt

data class Point3(val x: Float, val y: Float, val z: Float)

fun Pose.point() = Point3(tx(), ty(), tz())

fun distance(a: Point3, b: Point3): Double {
    val dx = (b.x - a.x).toDouble()
    val dy = (b.y - a.y).toDouble()
    val dz = (b.z - a.z).toDouble()
    return sqrt(dx * dx + dy * dy + dz * dz)
}

fun rectangleMetrics(a: Point3, b: Point3): Triple<Double, Double, Double> {
    val height = abs((b.y - a.y).toDouble())
    val dx = (b.x - a.x).toDouble()
    val dz = (b.z - a.z).toDouble()
    val width = sqrt(dx * dx + dz * dz)
    return Triple(width, height, width * height)
}

/** Area of a planar 3D polygon using the magnitude of its area vector. */
fun polygonArea(points: List<Point3>): Double {
    if (points.size < 3) return 0.0
    var x = 0.0; var y = 0.0; var z = 0.0
    points.indices.forEach { i ->
        val p = points[i]
        val q = points[(i + 1) % points.size]
        x += p.y * q.z - p.z * q.y
        y += p.z * q.x - p.x * q.z
        z += p.x * q.y - p.y * q.x
    }
    return 0.5 * sqrt(x * x + y * y + z * z)
}

fun perimeter(points: List<Point3>, closed: Boolean): Double {
    if (points.size < 2) return 0.0
    var result = points.zipWithNext().sumOf { distance(it.first, it.second) }
    if (closed && points.size > 2) result += distance(points.last(), points.first())
    return result
}
