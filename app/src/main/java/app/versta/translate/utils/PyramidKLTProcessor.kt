package app.versta.translate.utils

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import boofcv.abst.feature.detect.interest.ConfigPointDetector
import boofcv.abst.feature.detect.interest.PointDetectorTypes
import boofcv.abst.tracker.PointTrack
import boofcv.abst.tracker.PointTracker
import boofcv.alg.color.ColorFormat
import boofcv.alg.tracker.klt.ConfigPKlt
import boofcv.android.ConvertCameraImage
import boofcv.factory.feature.detect.selector.ConfigSelectLimit
import boofcv.factory.tracker.FactoryPointTracker
import boofcv.struct.geo.AssociatedPair
import boofcv.struct.image.GrayS16
import boofcv.struct.image.GrayU8
import boofcv.struct.pyramid.ConfigDiscreteLevels
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import georegression.struct.point.Point2D_F64
import org.ddogleg.struct.DogArray


class PyramidKLTProcessor(
    private val scale: Float = 1.0f,
    private val significantChangeThreshold: Float = 0.1f,
    private val onSignificantChange: () -> Unit = {},
    private val onFrameProcessed: (List<AssociatedPair>, Long) -> Unit
) : ImageAnalysis.Analyzer {
    private val _maxFeatures = 100
    private val _respawnThreshold = 50

    private val _configDetector = ConfigPointDetector().apply {
        type = PointDetectorTypes.SHI_TOMASI
        shiTomasi.radius = 3
        general.maxFeatures = 10 + _maxFeatures
        general.threshold = 0.05f
        general.radius = 4
        general.selector = ConfigSelectLimit.selectUniform(10.0)
    }

    private val _configPyramidKLT = ConfigPKlt().apply {
        pyramidLevels = ConfigDiscreteLevels.minSize(40)
        templateRadius = 3
        maximumTracks.setFixed(this@PyramidKLTProcessor._configDetector.general.maxFeatures.toDouble())
        toleranceFB = 2.0
    }

    private var _tracker: PointTracker<GrayU8> = FactoryPointTracker.klt(
        _configPyramidKLT, _configDetector, GrayU8::class.java, GrayS16::class.java
    )

    private var _shouldResetOnNextIteration = false

    private val _pairs: DogArray<AssociatedPair> = DogArray { AssociatedPair() }

    private val _active: MutableList<PointTrack> = ArrayList()
    private val _inactive: MutableList<PointTrack> = ArrayList()
    private val _spawned: MutableList<PointTrack> = ArrayList()

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: return
        val image = GrayU8(mediaImage.width, mediaImage.height)

        ConvertCameraImage.imageToBoof(mediaImage, ColorFormat.GRAY, image, null)

        process(image)
            .addOnSuccessListener { trackedFrame ->
                val changeDelta = getChangeDelta(trackedFrame)
                if (changeDelta > significantChangeThreshold) {
                    Log.d(TAG, "Change delta: $changeDelta, ${_spawned.size}")

                    onSignificantChange()
                }

                onFrameProcessed(trackedFrame.toList(), imageProxy.imageInfo.timestamp)
            }.addOnFailureListener {
                Log.e(TAG, "Pyramid KLT failed", it)
            }.addOnCompleteListener {
                if (_shouldResetOnNextIteration) {
                    reset()
                }

                imageProxy.close()
            }

    }

    fun clear() {
        _shouldResetOnNextIteration = true
    }

    private fun reset() {
        _tracker.dropAllTracks()
        _tracker.reset()

        _pairs.reset()

        _shouldResetOnNextIteration = false
    }

    private fun process(image: GrayU8): Task<DogArray<AssociatedPair>> {
        try {
            _tracker.process(image)

            _inactive.clear()
            _active.clear()
            _spawned.clear()

            _tracker.getInactiveTracks(_inactive)
            _inactive.forEach {
                if (_tracker.frameID - it.lastSeenFrameID > 2) {
                    _tracker.dropTrack(it)
                }
            }

            _tracker.getActiveTracks(_active)
            if (_active.size < _respawnThreshold) {
                _tracker.spawnTracks()

                _active.forEach {
                    val info = it.getCookie<TrackInfo>()
                    info.start.setTo(it.pixel)
                }

                _tracker.getNewTracks(_spawned)
                _spawned.forEach {
                    if (it.cookie == null) {
                        it.cookie = TrackInfo()
                    }

                    val info = it.getCookie<TrackInfo>()
                    info.start.setTo(it.pixel)
                }
            }

            _pairs.reset()
            _active.forEach {
                val info = it.getCookie<TrackInfo>()

                val start = info.start.copy()
                val current = it.pixel.copy()

                start.scale(scale.toDouble())
                current.scale(scale.toDouble())

                _pairs.grow().setTo(AssociatedPair(start, current))
            }

            return Tasks.forResult(_pairs)
        } catch (e: Exception) {
            return Tasks.forException(e)
        }
    }

    private fun getChangeDelta(pairs: DogArray<AssociatedPair>): Float {
        var changeDelta = 0.0f
        pairs.forEach {
            changeDelta += it.p1.distance(it.p2).toFloat()
        }
        return changeDelta / pairs.size
    }

    companion object {
        private val TAG: String = PyramidKLTProcessor::class.java.simpleName

        private class TrackInfo {
            val start = Point2D_F64()
        }
    }
}