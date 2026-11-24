package app.versta.translate.core.entity

/**
 * Camera bring-up result for the Vision screen. Distinct from the camera
 * permission state: a granted permission does not imply a usable camera
 * (camera-less devices, missing camera HAL, CameraX init or bind failures).
 */
sealed class CameraState {
    /** No camera attempt has settled yet this session. */
    data object Unknown : CameraState()

    /** A camera is bound to the lifecycle and can stream frames. */
    data object Ready : CameraState()

    /** No usable camera on this device; the screen degrades to a placeholder. */
    data object Unavailable : CameraState()
}
