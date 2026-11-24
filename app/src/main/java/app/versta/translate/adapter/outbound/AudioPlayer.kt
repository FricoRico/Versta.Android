package app.versta.translate.adapter.outbound

interface AudioPlayer {
    /**
     * Plays mono PCM f32 audio of the given [sampleRate] in Hz.
     * @param audio The audio data to play.
     */
    fun play(
        audio: FloatArray,
        sampleRate: Int,
    )

    /**
     * Stops the audio playback.
     */
    fun stop()

    /**
     * Releases the resources used by the audio player.
     */
    fun release()
}
