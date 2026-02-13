package com.jetbrains.kmpapp.model

import kotlinx.coroutines.flow.Flow

/**
 * Model download status
 */
sealed class ModelDownloadStatus {
    data object Idle : ModelDownloadStatus()
    data object Checking : ModelDownloadStatus()
    data class Downloading(val progress: Float, val downloaded: Long, val total: Long) : ModelDownloadStatus()
    data class Downloaded(val modelId: String) : ModelDownloadStatus()
    data class Failed(val error: String) : ModelDownloadStatus()
}

/**
 * Network type for download
 */
enum class NetworkType {
    NONE,
    WIFI,
    CELLULAR,
    ETHERNET
}

/**
 * Model information
 */
data class ModelInfo(
    val id: String,
    val name: String,
    val displayName: String,
    val language: String?,
    val size: Long,
    val downloadUrl: String,
    val localPath: String?,
    val version: String,
    val isDownloaded: Boolean = false,
    val checksumMd5: String? = null
)

/**
 * Model download result
 */
sealed class ModelDownloadResult {
    data class Success(val modelInfo: ModelInfo) : ModelDownloadResult()
    data class Error(val message: String, val cause: Throwable? = null) : ModelDownloadResult()
    data object Cancelled : ModelDownloadResult()
}

/**
 * Network manager for checking connectivity
 */
interface NetworkManager {
    /**
     * Get current network type
     */
    fun getCurrentNetworkType(): NetworkType

    /**
     * Check if connected to WiFi
     */
    fun isWiFiConnected(): Boolean

    /**
     * Check if any network is available
     */
    fun isNetworkAvailable(): Boolean

    /**
     * Observe network changes
     */
    fun observeNetworkChanges(): Flow<NetworkType>
}

/**
 * Model downloader interface
 */
interface ModelDownloader {
    /**
     * Check if model is available locally
     */
    suspend fun isModelAvailable(modelId: String): Boolean

    /**
     * Get model info
     */
    suspend fun getModelInfo(modelId: String): ModelInfo?

    /**
     * Download model with progress
     */
    suspend fun downloadModel(
        modelInfo: ModelInfo,
        onProgress: (ModelDownloadStatus) -> Unit
    ): ModelDownloadResult

    /**
     * Delete model from local storage
     */
    suspend fun deleteModel(modelId: String): Boolean

    /**
     * Get all available models
     */
    suspend fun getAvailableModels(): List<ModelInfo>

    /**
     * Get downloaded models
     */
    suspend fun getDownloadedModels(): List<ModelInfo>

    /**
     * Cancel ongoing download
     */
    fun cancelDownload()
}

/**
 * Model manager that coordinates network check and model downloads
 */
interface ModelManager {
    /**
     * Check and download model if needed
     * - Checks if model exists locally
     * - Checks WiFi connection
     * - Requests user confirmation if on cellular
     * - Downloads model
     */
    suspend fun ensureModelAvailable(
        modelId: String,
        forceDownload: Boolean = false,
        onConfirmationNeeded: suspend (ModelInfo, NetworkType) -> Boolean,
        onProgress: (ModelDownloadStatus) -> Unit
    ): ModelDownloadResult

    /**
     * Get network manager
     */
    fun getNetworkManager(): NetworkManager

    /**
     * Get model downloader
     */
    fun getModelDownloader(): ModelDownloader
}

/**
 * Platform-specific network manager factory
 */
expect fun createNetworkManager(): NetworkManager

/**
 * Platform-specific model downloader factory
 */
expect fun createModelDownloader(): ModelDownloader

/**
 * Platform-specific model manager factory
 */
expect fun createModelManager(): ModelManager
