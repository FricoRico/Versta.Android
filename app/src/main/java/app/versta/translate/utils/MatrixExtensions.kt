package app.versta.translate.utils

import android.graphics.Matrix
import android.graphics.PointF

/** Maps [points] through this matrix, packed as a flat x,y array. */
fun Matrix.mapToArray(points: List<PointF>): FloatArray {
    val out = FloatArray(points.size * 2)
    points.forEachIndexed { i, p ->
        out[i * 2] = p.x
        out[i * 2 + 1] = p.y
    }
    mapPoints(out)
    return out
}

/** Maps [points] through this matrix, back as points. */
fun Matrix.mapPoints(points: List<PointF>): List<PointF> {
    val flat = mapToArray(points)
    return (0 until points.size).map { PointF(flat[it * 2], flat[it * 2 + 1]) }
}
