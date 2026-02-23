package com.jetbrains.kmpapp.tts

/**
 * iOS TTS Engine using Sherpa-ONNX VITS/Piper.
 * Placeholder - will be fully implemented in Aşama 3.
 * Uses FP16 models for better quality on modern iPhones.
 */
class IOSTTSEngine : TTSEngine {
    override val engineId: String = "ios-tts-sherpa-onnx"

    private var initialized = false
    private var currentModelId: String? = null
    private var speakingFlag = false

    override suspend fun initialize(modelId: String, config: Map<String, Any>) {
        currentModelId = modelId
        // TODO: Initialize Sherpa-ONNX TTS via C interop
        initialized = true
    }

    override suspend fun isReady(): Boolean = initialized

    override suspend fun release() {
        initialized = false
        currentModelId = null
    }

    override fun getMemoryUsageBytes(): Long = if (initialized) 80 * 1024 * 1024L else 0L

    override fun getEstimatedMemoryBytes(): Long = 80 * 1024 * 1024L

    override suspend fun synthesize(text: String, config: TTSConfig): TTSResult {
        return TTSResult(
            audioData = FloatArray(0),
            sampleRate = config.sampleRate,
            durationMs = 0L
        )
    }

    override fun stop() {
        speakingFlag = false
    }

    override fun isSpeaking(): Boolean = speakingFlag
}
