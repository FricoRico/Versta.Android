package app.versta.translate.adapter.outbound

class AudioMockPlayer : AudioPlayer {
    /**
     * Plays mono PCM f32 audio of the given [sampleRate] in Hz.
     * @param audio The audio data to play.
     */
    override fun play(
        audio: FloatArray,
        sampleRate: Int,
    ) {
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
