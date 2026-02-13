package com.jetbrains.kmpapp.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for model management
 */
data class ModelManagerUiState(
    val availableModels: List<ModelInfo> = emptyList(),
    val downloadedModels: List<ModelInfo> = emptyList(),
    val currentDownload: ModelDownloadStatus = ModelDownloadStatus.Idle,
    val networkType: NetworkType = NetworkType.NONE,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showConfirmationDialog: Boolean = false,
    val pendingDownload: ModelInfo? = null
)

/**
 * ViewModel for managing model downloads
 */
class ModelManagerViewModel(
    private val modelManager: ModelManager,
    private val scope: CoroutineScope
) {
    private val _uiState = MutableStateFlow(ModelManagerUiState())
    val uiState: StateFlow<ModelManagerUiState> = _uiState.asStateFlow()

    private var downloadJob: Job? = null
    private val modelDownloader = modelManager.getModelDownloader()
    private val networkManager = modelManager.getNetworkManager()

    init {
        loadModels()
        observeNetworkChanges()
    }

    private fun observeNetworkChanges() {
        scope.launch {
            networkManager.observeNetworkChanges().collect { networkType ->
                _uiState.value = _uiState.value.copy(networkType = networkType)
            }
        }
    }

    fun loadModels() {
        scope.launch(Dispatchers.Default) {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val available = modelDownloader.getAvailableModels()
                val downloaded = modelDownloader.getDownloadedModels()
                val networkType = networkManager.getCurrentNetworkType()

                _uiState.value = _uiState.value.copy(
                    availableModels = available,
                    downloadedModels = downloaded,
                    networkType = networkType,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load models: ${e.message}"
                )
            }
        }
    }

    fun downloadModel(modelInfo: ModelInfo, forceDownload: Boolean = false) {
        downloadJob?.cancel()

        downloadJob = scope.launch(Dispatchers.Default) {
            _uiState.value = _uiState.value.copy(
                currentDownload = ModelDownloadStatus.Checking,
                error = null
            )

            val result = modelManager.ensureModelAvailable(
                modelId = modelInfo.id,
                forceDownload = forceDownload,
                onConfirmationNeeded = { model, networkType ->
                    // Show confirmation dialog
                    _uiState.value = _uiState.value.copy(
                        showConfirmationDialog = true,
                        pendingDownload = model
                    )

                    // Wait for user confirmation
                    // This is handled by confirmDownload() or cancelDownload()
                    false // Return false for now, will be handled by user action
                },
                onProgress = { status ->
                    _uiState.value = _uiState.value.copy(currentDownload = status)
                }
            )

            when (result) {
                is ModelDownloadResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        currentDownload = ModelDownloadStatus.Downloaded(modelInfo.id),
                        error = null
                    )
                    loadModels() // Refresh list
                }

                is ModelDownloadResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        currentDownload = ModelDownloadStatus.Failed(result.message),
                        error = result.message
                    )
                }

                is ModelDownloadResult.Cancelled -> {
                    _uiState.value = _uiState.value.copy(
                        currentDownload = ModelDownloadStatus.Idle,
                        error = null
                    )
                }
            }
        }
    }

    fun downloadModelWithConfirmation(modelInfo: ModelInfo) {
        val networkType = networkManager.getCurrentNetworkType()

        // If WiFi or Ethernet, download directly
        if (networkType == NetworkType.WIFI || networkType == NetworkType.ETHERNET) {
            downloadModel(modelInfo)
        } else {
            // Show confirmation dialog
            _uiState.value = _uiState.value.copy(
                showConfirmationDialog = true,
                pendingDownload = modelInfo
            )
        }
    }

    fun confirmDownload() {
        val pendingModel = _uiState.value.pendingDownload
        if (pendingModel != null) {
            _uiState.value = _uiState.value.copy(
                showConfirmationDialog = false,
                pendingDownload = null
            )
            downloadModel(pendingModel)
        }
    }

    fun cancelDownload() {
        _uiState.value = _uiState.value.copy(
            showConfirmationDialog = false,
            pendingDownload = null,
            currentDownload = ModelDownloadStatus.Idle
        )
        downloadJob?.cancel()
        modelDownloader.cancelDownload()
    }

    fun deleteModel(modelId: String) {
        scope.launch(Dispatchers.Default) {
            try {
                val success = modelDownloader.deleteModel(modelId)
                if (success) {
                    loadModels() // Refresh list
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to delete model"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error deleting model: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun dismissConfirmationDialog() {
        _uiState.value = _uiState.value.copy(
            showConfirmationDialog = false,
            pendingDownload = null
        )
    }
}
