package app.versta.translate.core.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ObjectCharacterRecognitionModule {
    @SerialName("detector")
    Detector,

    @SerialName("recognizer")
    Recognizer,

    @SerialName("scriptClassifier")
    ScriptClassifier,

    @SerialName("textlineOrientation")
    TextlineOrientation,

    @SerialName("aligner")
    Aligner,

    @SerialName("glyphmatte")
    GlyphMatte
}
