package app.versta.translate.adapter.outbound

interface AudioPlayer {
    /**
     * Plays the audio file at the given path.
     * @param audio The audio data to play.
     */
    fun play(audio: FloatArray)

    /**
     * Plays the audio file at the given path.
     * @param audio The audio data to play.
     */
    fun play(audio: ByteArray)

    /**
     * Stops the audio playback.
     */
    fun stop()

    /**
     * Releases the resources used by the audio player.
     */
    fun release()
}