package app.versta.translate.core.entity

import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.core.graphics.toRectF
import com.google.mlkit.vision.text.Text

class TrackedText {
    private var _timestamp = 0L
    private var _text: Text? = null

    val timestamp: Long
        get() = _timestamp

    val text: String
        get() = _text?.text ?: ""

    val blocks get() = _text?.textBlocks ?: emptyList()

    fun update(
        timestamp: Long = 0L,
        text: Text
    ) {
        _timestamp = timestamp
        _text = text
    }

    fun clear() {
        _timestamp = 0L
        _text = null
    }

    fun boundingRect(expand: Float = 0f): RectF {
        val boundingRect = RectF()

        _text?.textBlocks?.forEach { block ->
            val rect = block.boundingBox?.toRectF() ?: return@forEach

            boundingRect.union(rect)
        }

        boundingRect.inset(-expand, -expand)
        return boundingRect
    }
}