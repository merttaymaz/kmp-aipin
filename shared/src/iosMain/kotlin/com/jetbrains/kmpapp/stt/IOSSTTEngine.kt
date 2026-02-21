package com.jetbrains.kmpapp.stt

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * iOS STT Engine using Sherpa-ONNX.
 * Placeholder - will be fully implemented in Aşama 2.
 * Uses FP16 models for better quality on modern iPhones.
 */
class IOSSTTEngine : STTEngine {
    override val engineId: String = "ios-stt-sherpa-onnx"

    private var initialized = false
    private var currentModelId: String? = null

    override suspend fun initialize(modelId: String, config: Map<String, Any>) {
        currentModelId = modelId
        // TODO: Initialize Sherpa-ONNX via C interop
        initialized = true
    }

    override suspend fun isReady(): Boolean = initialized

    override suspend fun release() {
        initialized = false
        currentModelId = null
    }

    override fun getMemoryUsageBytes(): Long = if (initialized) 120 * 1024 * 1024L else 0L

    override fun getEstimatedMemoryBytes(): Long = 120 * 1024 * 1024L

    override fun startRecognition(config: STTConfig): Flow<STTResult> {
        return emptyFlow()
    }

    override fun processAudioData(audioData: ShortArray) {}

    override fun stopRecognition() {}

    override fun isRecognizing(): Boolean = false
}
