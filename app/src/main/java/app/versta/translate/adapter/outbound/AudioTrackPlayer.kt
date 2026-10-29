package app.versta.translate.adapter.outbound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioFormat.CHANNEL_OUT_MONO
import android.media.AudioTrack
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

class AudioTrackPlayer : AudioPlayer {
    private val channelMask = CHANNEL_OUT_MONO
    private val encoding = AudioFormat.ENCODING_PCM_16BIT

    private var sampleRate = 0
    private var audioTrack: AudioTrack? = null

    /**
     * Plays mono PCM f32 audio of the given [sampleRate] in Hz, rebuilding the
     * underlying track when the rate changes.
     * @param audio The audio data to play.
     */
    override fun play(
        audio: FloatArray,
        sampleRate: Int,
    ) {
        val track = trackFor(sampleRate)

        val byteBuffer = ByteBuffer.allocate(audio.size * 2)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)

        for (sample in audio) {
            val scaled = (max(-1f, min(1f, sample)) * Short.MAX_VALUE).toInt().toShort()
            byteBuffer.putShort(scaled)
        }

        track.play()
        track.write(byteBuffer.array(), 0, byteBuffer.array().size)
    }

    /**
     * Stops the audio playback.
     */
    override fun stop() {
        audioTrack?.stop()
    }

    /**
     * Releases the resources used by the audio player.
     */
    override fun release() {
        audioTrack?.release()
        audioTrack = null
    }

    private fun trackFor(sampleRate: Int): AudioTrack {
        val track = audioTrack
        if (track != null && sampleRate == this.sampleRate) {
            return track
        }

        track?.release()

        return buildAudioTrack(sampleRate).also {
            this.sampleRate = sampleRate
            audioTrack = it
        }
    }

    private fun buildAudioTrack(sampleRate: Int): AudioTrack =
        AudioTrack
            .Builder()
            .apply {
                setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build(),
                )
                setAudioFormat(
                    AudioFormat
                        .Builder()
                        .setEncoding(encoding)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelMask)
                        .build(),
                )
                setBufferSizeInBytes(AudioTrack.getMinBufferSize(sampleRate, channelMask, encoding))
            }.build()

    companion object {
        private val TAG = AudioTrackPlayer::class.java.simpleName
    }
}
