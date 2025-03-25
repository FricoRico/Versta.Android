package app.versta.translate.adapter.outbound

import android.media.AudioFormat
import android.media.AudioFormat.CHANNEL_OUT_MONO
import android.media.AudioTrack
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal const val SAMPLE_RATE = 24000

class AudioTrackPlayer : AudioPlayer {
    private val channelMask = CHANNEL_OUT_MONO
    private val encoding = AudioFormat.ENCODING_PCM_16BIT
    private val _audioTrack = AudioTrack.Builder().apply {
        setAudioAttributes(
            android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .build()
        )
        setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(encoding)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(channelMask)
                .build()
        )
        setBufferSizeInBytes(AudioTrack.getMinBufferSize(SAMPLE_RATE, channelMask, encoding))
    }.build()

    /**
     * Plays the audio file at the given path.
     * @param audio The audio data to play.
     */
    override fun play(audio: FloatArray) {
        val byteBuffer = ByteBuffer.allocate(audio.size * 2)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)

        for (sample in audio) {
            byteBuffer.putShort((sample * SAMPLE_RATE).toInt().toShort())
        }

        _audioTrack.play()
        _audioTrack.write(byteBuffer.array(), 0, byteBuffer.array().size)
    }

    /**
     * Plays the audio file at the given path.
     * @param audio The audio data to play.
     */
    override fun play(audio: ByteArray) {
        _audioTrack.play()
        _audioTrack.write(audio, 0, audio.size)
    }

    /**
     * Stops the audio playback.
     */
    override fun stop() {
        _audioTrack.stop()
    }

    /**
     * Releases the resources used by the audio player.
     */
    override fun release() {
        _audioTrack.release()
    }

    companion object {
        private val TAG = AudioTrackPlayer::class.java.simpleName
    }
}