package app.versta.translate.core.entity

import android.graphics.PointF
import androidx.compose.ui.graphics.Color

class ObjectCharacterRecogniserColors(
    val background: Color,
    val foreground: Color
)

class ObjectCharacterRecogniserResult(
    val points: Array<PointF> = arrayOf(),
    var score: Float = 0f,
    var tokens: LongArray = longArrayOf(),
    var text: String = "",
    var colors: ObjectCharacterRecogniserColors = ObjectCharacterRecogniserColors(Color.Black, Color.White)
)