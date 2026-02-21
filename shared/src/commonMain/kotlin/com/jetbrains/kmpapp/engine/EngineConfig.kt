package com.jetbrains.kmpapp.engine

import com.jetbrains.kmpapp.model.EngineModelType

/**
 * Configuration for an engine model.
 * Describes a downloadable/loadable model with its metadata.
 */
data class EngineModelConfig(
    val modelId: String,
    val modelType: EngineModelType,
    val language: String,
    val targetLanguage: String? = null,
    val backendType: String,
    val modelFiles: Map<String, String>,
    val downloadUrl: String,
    val sizeBytes: Long,
    val checksum: String? = null,
    val displayName: String = modelId,
    val quantization: ModelQuantization = ModelQuantization.INT8
)

/**
 * Model quantization level.
 * Android 4GB devices use INT8 for lower RAM usage.
 * iOS devices can use FP16 for better quality.
 */
enum class ModelQuantization {
    INT8,
    FP16,
    FP32
}

/**
 * Memory loading strategy, determined by available device RAM.
 */
enum class MemoryStrategy {
    /** Android 4GB: Load one model at a time, release before loading next */
    SEQUENTIAL,
    /** iOS 6-8GB: Keep all models in RAM simultaneously */
    CONCURRENT
}
