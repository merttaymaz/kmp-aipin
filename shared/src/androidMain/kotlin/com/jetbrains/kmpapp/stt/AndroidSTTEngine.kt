package com.jetbrains.kmpapp.stt

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Android STT Engine using Sherpa-ONNX.
 * Placeholder - will be fully implemented in Aşama 2 by wrapping AndroidSpeechRecognizer.
 */
class AndroidSTTEngine : STTEngine {
    override val engineId: String = "android-stt-sherpa-onnx"

    private var initialized = false
    private var currentModelId: String? = null

    override suspend fun initialize(modelId: String, config: Map<String, Any>) {
        currentModelId = modelId
        // TODO: Initialize Sherpa-ONNX recognizer with model files
        initialized = true
    }

    override suspend fun isReady(): Boolean = initialized

    override suspend fun release() {
        // TODO: Release Sherpa-ONNX resources
        initialized = false
        currentModelId = null
    }

    override fun getMemoryUsageBytes(): Long = if (initialized) 40 * 1024 * 1024L else 0L

    override fun getEstimatedMemoryBytes(): Long = 40 * 1024 * 1024L

    override fun startRecognition(config: STTConfig): Flow<STTResult> {
        // TODO: Implement with Sherpa-ONNX
        return emptyFlow()
    }

    override fun processAudioData(audioData: ShortArray) {
        // TODO: Feed audio to Sherpa-ONNX
    }

    override fun stopRecognition() {
        // TODO: Stop recognition
    }

    override fun isRecognizing(): Boolean = false
}
