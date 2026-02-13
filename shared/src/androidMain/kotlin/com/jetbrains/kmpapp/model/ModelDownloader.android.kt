package com.jetbrains.kmpapp.model

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

actual fun createModelDownloader(): ModelDownloader {
    throw IllegalStateException("Use AndroidModelDownloader(context) instead")
}

class AndroidModelDownloader(private val context: Context) : ModelDownloader {

    private var isCancelled = false

    private val modelsDirectory: File by lazy {
        File(context.filesDir, "models").apply {
            if (!exists()) mkdirs()
        }
    }

    // Model catalog - In production, this could come from a remote config
    private val modelCatalog = mapOf(
        "whisper-tiny-tr" to ModelInfo(
            id = "whisper-tiny-tr",
            name = "whisper-tiny",
            displayName = "Whisper Tiny (Turkish)",
            language = "tr",
            size = 40 * 1024 * 1024L, // 40 MB
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2",
            localPath = null,
            version = "1.0.0"
        ),
        "whisper-base-tr" to ModelInfo(
            id = "whisper-base-tr",
            name = "whisper-base",
            displayName = "Whisper Base (Turkish)",
            language = "tr",
            size = 75 * 1024 * 1024L, // 75 MB
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherper-onnx-whisper-base.tar.bz2",
            localPath = null,
            version = "1.0.0"
        ),
        "whisper-small-tr" to ModelInfo(
            id = "whisper-small-tr",
            name = "whisper-small",
            displayName = "Whisper Small (Turkish)",
            language = "tr",
            size = 245 * 1024 * 1024L, // 245 MB
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
        val modelDir = File(modelsDirectory, modelId)
        if (!modelDir.exists()) return@withContext false

        // Check if all required files exist
        val requiredFiles = listOf("whisper-encoder.onnx", "whisper-decoder.onnx", "whisper-tokens.txt")
        requiredFiles.all { fileName ->
            File(modelDir, fileName).exists()
        }
    }

    override suspend fun getModelInfo(modelId: String): ModelInfo? {
        val catalogModel = modelCatalog[modelId] ?: return null
        val localPath = if (isModelAvailable(modelId)) {
            File(modelsDirectory, modelId).absolutePath
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

            val modelDir = File(modelsDirectory, modelInfo.id)
            if (!modelDir.exists()) {
                modelDir.mkdirs()
            }

            // Download the tar.bz2 file
            val tempFile = File(context.cacheDir, "${modelInfo.id}.tar.bz2")

            val url = URL(modelInfo.downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            try {
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext ModelDownloadResult.Error(
                        "Server returned ${connection.responseCode}: ${connection.responseMessage}"
                    )
                }

                val totalSize = connection.contentLength.toLong()
                var downloadedSize = 0L

                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (isCancelled) {
                                tempFile.delete()
                                return@withContext ModelDownloadResult.Cancelled
                            }

                            output.write(buffer, 0, bytesRead)
                            downloadedSize += bytesRead

                            val progress = if (totalSize > 0) {
                                downloadedSize.toFloat() / totalSize.toFloat()
                            } else {
                                0f
                            }

                            onProgress(
                                ModelDownloadStatus.Downloading(
                                    progress = progress,
                                    downloaded = downloadedSize,
                                    total = totalSize
                                )
                            )
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }

            // Extract tar.bz2 (simplified - in production use a proper library)
            // For now, we'll assume the files are extracted manually or use assets
            // This is a placeholder for actual extraction logic

            // Verify checksums if available
            if (modelInfo.checksumMd5 != null) {
                val actualChecksum = calculateMD5(tempFile)
                if (actualChecksum != modelInfo.checksumMd5) {
                    tempFile.delete()
                    return@withContext ModelDownloadResult.Error("Checksum verification failed")
                }
            }

            // Move to final location (placeholder)
            // In production, extract tar.bz2 here

            tempFile.delete()

            val updatedModelInfo = modelInfo.copy(
                localPath = modelDir.absolutePath,
                isDownloaded = true
            )

            onProgress(ModelDownloadStatus.Downloaded(modelInfo.id))
            ModelDownloadResult.Success(updatedModelInfo)

        } catch (e: Exception) {
            ModelDownloadResult.Error("Download failed: ${e.message}", e)
        }
    }

    override suspend fun deleteModel(modelId: String): Boolean = withContext(Dispatchers.IO) {
        val modelDir = File(modelsDirectory, modelId)
        if (modelDir.exists()) {
            modelDir.deleteRecursively()
        } else {
            false
        }
    }

    override suspend fun getAvailableModels(): List<ModelInfo> {
        return modelCatalog.values.map { catalogModel ->
            val localPath = if (isModelAvailable(catalogModel.id)) {
                File(modelsDirectory, catalogModel.id).absolutePath
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
    }

    private fun calculateMD5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
