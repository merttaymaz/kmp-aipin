package com.jetbrains.kmpapp.tts

/**
 * Android TTS Engine using Sherpa-ONNX VITS/Piper.
 * Placeholder - will be fully implemented in Aşama 3.
 * Uses INT8 quantized models for 4GB RAM devices.
 */
class AndroidTTSEngine : TTSEngine {
    override val engineId: String = "android-tts-sherpa-onnx"

    private var initialized = false
    private var currentModelId: String? = null
    private var speakingFlag = false

    override suspend fun initialize(modelId: String, config: Map<String, Any>) {
        currentModelId = modelId
        // TODO: Initialize Sherpa-ONNX TTS with model files
        initialized = true
    }

    override suspend fun isReady(): Boolean = initialized

    override suspend fun release() {
        // TODO: Release Sherpa-ONNX TTS resources
        initialized = false
        currentModelId = null
    }

    override fun getMemoryUsageBytes(): Long = if (initialized) 30 * 1024 * 1024L else 0L

    override fun getEstimatedMemoryBytes(): Long = 30 * 1024 * 1024L

    override suspend fun synthesize(text: String, config: TTSConfig): TTSResult {
        // TODO: Implement Sherpa-ONNX TTS synthesis
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
