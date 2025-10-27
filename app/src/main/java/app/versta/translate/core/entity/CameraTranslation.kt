package app.versta.translate.core.entity

import android.graphics.PointF

class CameraTranslationResult(
    val points: Array<PointF>,
    var score: Float,
    val text: String,
    val translated: String,
    var colors: ObjectCharacterRecogniserColors
)