package com.jetbrains.kmpapp.engine

/**
 * Base engine interface for all AI engines (STT, TTS, Translation).
 * Each engine manages a single model lifecycle: initialize → use → release.
 */
interface Engine {
    val engineId: String

    /**
     * Initialize the engine with a specific model.
     * On Android (4GB), only one engine should be initialized at a time (sequential).
     * On iOS (6-8GB), multiple engines can be initialized concurrently.
     */
    suspend fun initialize(modelId: String, config: Map<String, Any> = emptyMap())

    /** Check if the engine is initialized and ready to use */
    suspend fun isReady(): Boolean

    /**
     * Release model from memory.
     * Critical for Android 4GB devices to free RAM between pipeline stages.
     */
    suspend fun release()

    /** Current RAM usage in bytes (0 if not initialized) */
    fun getMemoryUsageBytes(): Long

    /** Estimated RAM requirement for the current/last model */
    fun getEstimatedMemoryBytes(): Long
}
