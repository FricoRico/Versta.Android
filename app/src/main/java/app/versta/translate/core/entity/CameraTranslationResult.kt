package app.versta.translate.core.entity

import android.graphics.PointF
import androidx.compose.ui.unit.dp

class CameraTranslationResult(
    val points: Array<PointF>,
    var score: Float,
    val text: String,
    val translated: String,
    var colors: ObjectCharacterRecogniserColors,
    val fontSize: Float = 16.dp.value,
    val lineHeight: Float = 16.dp.value,
    val fontWeight: FontWeight = FontWeight.REGULAR
)