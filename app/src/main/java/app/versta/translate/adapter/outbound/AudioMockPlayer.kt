package app.versta.translate.adapter.outbound

class AudioMockPlayer : AudioPlayer {
    /**
     * Plays the audio file at the given path.
     * @param audio The audio data to play.
     */
    override fun play(audio: FloatArray) {
        return
    }

    /**
     * Plays the audio file at the given path.
     * @param audio The audio data to play.
     */
    override fun play(audio: ByteArray) {
        return
    }

    /**
     * Stops the audio playback.
     */
    override fun stop() {
        return
    }

    /**
     * Releases the resources used by the audio player.
     */
    override fun release() {
        return
    }
}