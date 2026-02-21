package com.jetbrains.kmpapp.audio

/**
 * Android AudioPlayer using AudioTrack API.
 * Placeholder - will be fully implemented in Aşama 3.
 */
class AndroidAudioPlayer : AudioPlayer {
    private var isPlayingFlag = false
    private var volume = 1.0f

    override fun play(audioData: FloatArray, sampleRate: Int) {
        // TODO: Implement with Android AudioTrack
        isPlayingFlag = true
    }

    override fun stop() {
        // TODO: Stop AudioTrack
        isPlayingFlag = false
    }

    override fun isPlaying(): Boolean = isPlayingFlag

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
    }

    override fun release() {
        stop()
        // TODO: Release AudioTrack resources
    }
}

actual fun createAudioPlayer(): AudioPlayer = AndroidAudioPlayer()
