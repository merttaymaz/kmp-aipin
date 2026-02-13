package com.jetbrains.kmpapp.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.*
import kotlinx.cinterop.*

actual fun createModelDownloader(): ModelDownloader {
    return IOSModelDownloader()
}

class IOSModelDownloader : ModelDownloader {

    private var currentTask: NSURLSessionDownloadTask? = null
    private var isCancelled = false

    private val modelsDirectory: String by lazy {
        val documentsPath = NSFileManager.defaultManager.URLsForDirectory(
            NSDocumentDirectory,
            NSUserDomainMask
        ).firstOrNull() as? NSURL
        val modelsPath = documentsPath?.path + "/models"

        // Create directory if it doesn't exist
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(modelsPath)) {
            fileManager.createDirectoryAtPath(
                modelsPath,
                withIntermediateDirectories = true,
                attributes = null,
                error = null
            )
        }

        modelsPath
    }

    // Model catalog
    private val modelCatalog = mapOf(
        "whisper-tiny-tr" to ModelInfo(
            id = "whisper-tiny-tr",
            name = "whisper-tiny",
            displayName = "Whisper Tiny (Turkish)",
            language = "tr",
            size = 40 * 1024 * 1024L,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2",
            localPath = null,
            version = "1.0.0"
        ),
        "whisper-base-tr" to ModelInfo(
            id = "whisper-base-tr",
            name = "whisper-base",
            displayName = "Whisper Base (Turkish)",
            language = "tr",
            size = 75 * 1024 * 1024L,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-base.tar.bz2",
            localPath = null,
            version = "1.0.0"
        ),
        "whisper-small-tr" to ModelInfo(
            id = "whisper-small-tr",
            name = "whisper-small",
            displayName = "Whisper Small (Turkish)",
            language = "tr",
            size = 245 * 1024 * 1024L,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-small.tar.bz2",
            localPath = null,
            version = "1.0.0"
        ),
        "whisper-base-en" to ModelInfo(
            id = "whisper-base-en",
            name = "whisper-base",
            displayName = "Whisper Base (English)",
            language = "en",
            size = 75 * 1024 * 1024L,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-base.tar.bz2",
            localPath = null,
            version = "1.0.0"
        )
    )

    override suspend fun isModelAvailable(modelId: String): Boolean = withContext(Dispatchers.IO) {
        val modelPath = "$modelsDirectory/$modelId"
        val fileManager = NSFileManager.defaultManager

        if (!fileManager.fileExistsAtPath(modelPath)) {
            return@withContext false
        }

        // Check if all required files exist
        val requiredFiles = listOf("whisper-encoder.onnx", "whisper-decoder.onnx", "whisper-tokens.txt")
        requiredFiles.all { fileName ->
            fileManager.fileExistsAtPath("$modelPath/$fileName")
        }
    }

    override suspend fun getModelInfo(modelId: String): ModelInfo? {
        val catalogModel = modelCatalog[modelId] ?: return null
        val localPath = if (isModelAvailable(modelId)) {
            "$modelsDirectory/$modelId"
        } else {
            null
        }
        return catalogModel.copy(
            localPath = localPath,
            isDownloaded = localPath != null
        )
    }

    override suspend fun downloadModel(
        modelInfo: ModelInfo,
        onProgress: (ModelDownloadStatus) -> Unit
    ): ModelDownloadResult = withContext(Dispatchers.IO) {
        isCancelled = false

        try {
            onProgress(ModelDownloadStatus.Checking)

            val modelPath = "$modelsDirectory/${modelInfo.id}"
            val fileManager = NSFileManager.defaultManager

            // Create model directory
            if (!fileManager.fileExistsAtPath(modelPath)) {
                fileManager.createDirectoryAtPath(
                    modelPath,
                    withIntermediateDirectories = true,
                    attributes = null,
                    error = null
                )
            }

            // Download using NSURLSession
            val url = NSURL(string = modelInfo.downloadUrl)
            val configuration = NSURLSessionConfiguration.defaultSessionConfiguration()
            val session = NSURLSession.sessionWithConfiguration(configuration)

            var downloadedSize = 0L
            var totalSize = modelInfo.size

            val request = NSMutableURLRequest(uRL = url)
            request.setHTTPMethod("GET")
            request.setTimeoutInterval(300.0) // 5 minutes timeout

            // Synchronous download for simplicity
            // In production, use delegate pattern for progress
            val semaphore = kotlin.native.concurrent.AtomicInt(0)
            var downloadError: NSError? = null
            var downloadedData: NSData? = null

            val task = session.dataTaskWithRequest(request) { data, response, error ->
                if (error != null) {
                    downloadError = error
                } else {
                    downloadedData = data
                    totalSize = data?.length ?: modelInfo.size
                }
                semaphore.value = 1
            }

            currentTask = task as? NSURLSessionDownloadTask
            task.resume()

            // Wait for completion (simplified)
            while (semaphore.value == 0 && !isCancelled) {
                NSThread.sleepForTimeInterval(0.1)
            }

            if (isCancelled) {
                task.cancel()
                return@withContext ModelDownloadResult.Cancelled
            }

            if (downloadError != null) {
                return@withContext ModelDownloadResult.Error(
                    "Download failed: ${downloadError?.localizedDescription}"
                )
            }

            if (downloadedData == null) {
                return@withContext ModelDownloadResult.Error("No data received")
            }

            // Save to temp file
            val tempPath = NSTemporaryDirectory() + "${modelInfo.id}.tar.bz2"
            downloadedData?.writeToFile(tempPath, atomically = true)

            onProgress(
                ModelDownloadStatus.Downloading(
                    progress = 0.8f,
                    downloaded = downloadedData!!.length.toLong(),
                    total = totalSize
                )
            )

            // Extract tar.bz2 (placeholder - requires actual extraction)
            // In production, use appropriate library or native tools

            // Clean up temp file
            fileManager.removeItemAtPath(tempPath, error = null)

            val updatedModelInfo = modelInfo.copy(
                localPath = modelPath,
                isDownloaded = true
            )

            onProgress(ModelDownloadStatus.Downloaded(modelInfo.id))
            ModelDownloadResult.Success(updatedModelInfo)

        } catch (e: Exception) {
            ModelDownloadResult.Error("Download failed: ${e.message}", e)
        }
    }

    override suspend fun deleteModel(modelId: String): Boolean = withContext(Dispatchers.IO) {
        val modelPath = "$modelsDirectory/$modelId"
        val fileManager = NSFileManager.defaultManager

        if (fileManager.fileExistsAtPath(modelPath)) {
            fileManager.removeItemAtPath(modelPath, error = null)
        } else {
            false
        }
    }

    override suspend fun getAvailableModels(): List<ModelInfo> {
        return modelCatalog.values.map { catalogModel ->
            val localPath = if (isModelAvailable(catalogModel.id)) {
                "$modelsDirectory/${catalogModel.id}"
            } else {
                null
            }
            catalogModel.copy(
                localPath = localPath,
                isDownloaded = localPath != null
            )
        }
    }

    override suspend fun getDownloadedModels(): List<ModelInfo> {
        return getAvailableModels().filter { it.isDownloaded }
    }

    override fun cancelDownload() {
        isCancelled = true
        currentTask?.cancel()
    }
}
