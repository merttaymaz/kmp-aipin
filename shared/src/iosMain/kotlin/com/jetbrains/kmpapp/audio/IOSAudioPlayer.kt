package com.jetbrains.kmpapp.audio

/**
 * iOS AudioPlayer using AVAudioPlayer.
 * Placeholder - will be fully implemented in Aşama 3.
 */
class IOSAudioPlayer : AudioPlayer {
    private var isPlayingFlag = false
    private var volume = 1.0f

    override fun play(audioData: FloatArray, sampleRate: Int) {
        // TODO: Implement with AVAudioPlayer/AVAudioEngine
        isPlayingFlag = true
    }

    override fun stop() {
        isPlayingFlag = false
    }

    override fun isPlaying(): Boolean = isPlayingFlag

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
    }

    override fun release() {
        stop()
    }
}

actual fun createAudioPlayer(): AudioPlayer = IOSAudioPlayer()
