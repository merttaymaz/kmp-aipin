package com.jetbrains.kmpapp.audio

/**
 * Audio playback interface for TTS output.
 * Plays PCM float audio data through the device speaker.
 */
interface AudioPlayer {
    fun play(audioData: FloatArray, sampleRate: Int)
    fun stop()
    fun isPlaying(): Boolean
    fun setVolume(volume: Float)
    fun release()
}

/**
 * Platform-specific audio player factory
 */
expect fun createAudioPlayer(): AudioPlayer
