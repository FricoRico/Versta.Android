package app.versta.translate.core.entity

import android.graphics.Matrix
import android.graphics.RectF
import boofcv.factory.geo.ConfigHomography
import boofcv.factory.geo.ConfigRansac
import boofcv.factory.geo.FactoryMultiViewRobust
import boofcv.struct.geo.AssociatedPair
import georegression.struct.homography.Homography2D_F64
import org.ddogleg.fitting.modelset.ModelMatcher

class TrackedFrame {
    private var _timestamp = 0L
    private var _pairs: List<AssociatedPair> = emptyList()

    val timestamp: Long
        get() = _timestamp

    val pairs: List<AssociatedPair>
        get() = _pairs

    fun update(
        timestamp: Long = 0L,
        pairs: List<AssociatedPair> = emptyList(),
    ) {
        _timestamp = timestamp
        _pairs = pairs
    }

    fun clear() {
        _timestamp = 0L
        _pairs = emptyList()
    }

    fun pointsInsideBoundingBox(boundingBox: RectF): List<AssociatedPair> {
        return _pairs.filter {
            boundingBox.contains(it.p1.x.toFloat(), it.p1.y.toFloat())
        }
    }

    fun computeTranslationMatrix(
        boundingBox: RectF,
    ): Matrix {
        val pointsInsideRect = pointsInsideBoundingBox(boundingBox)

        if (pointsInsideRect.isEmpty()) {
            return Matrix()
        }

        val maxIterations = 100
        val inlierThreshold = 4.0

        val configHomography = ConfigHomography(
            false
        )

        val ransacConfig = ConfigRansac(
            maxIterations,
            inlierThreshold
        )

        val ransac: ModelMatcher<Homography2D_F64, AssociatedPair> =
            FactoryMultiViewRobust.homographyRansac(
                configHomography, ransacConfig
            )

        if (!ransac.process(pointsInsideRect)) {
            return Matrix()
        }

        val refinedModel = ransac.modelParameters

        val matrix = Matrix()
        val values = floatArrayOf(
            refinedModel.a11.toFloat(), refinedModel.a12.toFloat(), refinedModel.a13.toFloat(),
            refinedModel.a21.toFloat(), refinedModel.a22.toFloat(), refinedModel.a23.toFloat(),
            refinedModel.a31.toFloat(), refinedModel.a32.toFloat(), refinedModel.a33.toFloat()
        )
        matrix.setValues(values)
        return matrix
    }
}