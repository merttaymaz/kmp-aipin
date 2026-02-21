package com.jetbrains.kmpapp.tts

import com.jetbrains.kmpapp.engine.Engine

/**
 * Text-to-Speech engine interface.
 * Uses Sherpa-ONNX VITS/Piper models for on-device speech synthesis.
 */
interface TTSEngine : Engine {
    /** Synthesize text to audio */
    suspend fun synthesize(text: String, config: TTSConfig = TTSConfig()): TTSResult

    /** Stop ongoing synthesis */
    fun stop()

    /** Check if currently speaking/synthesizing */
    fun isSpeaking(): Boolean
}

data class TTSConfig(
    val language: String = "en",
    val speed: Float = 1.0f,
    val speakerId: Int = 0,
    val sampleRate: Int = 22050
)

data class TTSResult(
    val audioData: FloatArray,
    val sampleRate: Int,
    val durationMs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as TTSResult
        return sampleRate == other.sampleRate &&
            durationMs == other.durationMs &&
            audioData.contentEquals(other.audioData)
    }

    override fun hashCode(): Int {
        var result = audioData.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + durationMs.hashCode()
        return result
    }
}
