package com.jetbrains.kmpapp.model

/**
 * Default implementation of ModelManager
 */
class DefaultModelManager(
    private val networkManager: NetworkManager,
    private val modelDownloader: ModelDownloader
) : ModelManager {

    override suspend fun ensureModelAvailable(
        modelId: String,
        forceDownload: Boolean,
        onConfirmationNeeded: suspend (ModelInfo, NetworkType) -> Boolean,
        onProgress: (ModelDownloadStatus) -> Unit
    ): ModelDownloadResult {
        try {
            // Step 1: Check if model already exists
            if (!forceDownload && modelDownloader.isModelAvailable(modelId)) {
                val modelInfo = modelDownloader.getModelInfo(modelId)
                if (modelInfo != null) {
                    onProgress(ModelDownloadStatus.Downloaded(modelId))
                    return ModelDownloadResult.Success(modelInfo)
                }
            }

            // Step 2: Get model info
            val modelInfo = modelDownloader.getModelInfo(modelId)
                ?: return ModelDownloadResult.Error("Model not found: $modelId")

            // Step 3: Check network availability
            if (!networkManager.isNetworkAvailable()) {
                return ModelDownloadResult.Error("No network connection available")
            }

            val networkType = networkManager.getCurrentNetworkType()

            // Step 4: Check if WiFi is available, if not ask for confirmation
            if (networkType != NetworkType.WIFI && networkType != NetworkType.ETHERNET) {
                val userConfirmed = onConfirmationNeeded(modelInfo, networkType)
                if (!userConfirmed) {
                    return ModelDownloadResult.Cancelled
                }
            }

            // Step 5: Download model
            onProgress(ModelDownloadStatus.Checking)
            return modelDownloader.downloadModel(modelInfo, onProgress)

        } catch (e: Exception) {
            return ModelDownloadResult.Error("Failed to download model: ${e.message}", e)
        }
    }

    override fun getNetworkManager(): NetworkManager = networkManager

    override fun getModelDownloader(): ModelDownloader = modelDownloader
}
