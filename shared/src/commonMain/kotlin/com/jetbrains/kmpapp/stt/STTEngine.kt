package com.jetbrains.kmpapp.stt

import com.jetbrains.kmpapp.engine.Engine
import kotlinx.coroutines.flow.Flow

/**
 * Speech-to-Text engine interface.
 * Wraps Sherpa-ONNX speech recognition with the generic Engine lifecycle.
 */
interface STTEngine : Engine {
    /** Start recognition session, returns a flow of results */
    fun startRecognition(config: STTConfig = STTConfig()): Flow<STTResult>

    /** Feed audio data to the recognizer (PCM 16-bit mono) */
    fun processAudioData(audioData: ShortArray)

    /** Stop the current recognition session */
    fun stopRecognition()

    /** Check if currently recognizing */
    fun isRecognizing(): Boolean
}

data class STTConfig(
    val language: String = "en",
    val sampleRate: Int = 16000,
    val enablePunctuation: Boolean = true
)

data class STTResult(
    val text: String,
    val isFinal: Boolean,
    val language: String? = null,
    val confidence: Float? = null
)
