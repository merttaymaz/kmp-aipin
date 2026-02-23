package com.jetbrains.kmpapp.model

import com.jetbrains.kmpapp.engine.EngineModelConfig
import com.jetbrains.kmpapp.engine.ModelQuantization

/**
 * Central catalog of all available AI models (STT, TTS, Translation).
 * Provides model discovery and selection based on type, language, and device capabilities.
 */
interface ModelRegistry {
    /** Get all available models, optionally filtered by type */
    fun getAvailableModels(type: EngineModelType? = null): List<EngineModelConfig>

    /** Get available models for a specific type and language */
    fun getAvailableModels(type: EngineModelType, language: String): List<EngineModelConfig>

    /** Get a specific model configuration by ID */
    fun getModelConfig(modelId: String): EngineModelConfig?

    /**
     * Get all required models for a complete translation pipeline.
     * Returns STT (source language) + Translation (source→target) + TTS (target language).
     */
    fun getRequiredModelsForPipeline(
        sourceLanguage: String,
        targetLanguage: String
    ): List<EngineModelConfig>
}

/**
 * Default model registry with built-in model catalog.
 * Initial support: EN↔TR with OPUS-MT translation.
 */
class DefaultModelRegistry : ModelRegistry {

    private val models: List<EngineModelConfig> = buildModelCatalog()

    override fun getAvailableModels(type: EngineModelType?): List<EngineModelConfig> {
        return if (type != null) models.filter { it.modelType == type } else models
    }

    override fun getAvailableModels(type: EngineModelType, language: String): List<EngineModelConfig> {
        return models.filter { it.modelType == type && it.language == language }
    }

    override fun getModelConfig(modelId: String): EngineModelConfig? {
        return models.find { it.modelId == modelId }
    }

    override fun getRequiredModelsForPipeline(
        sourceLanguage: String,
        targetLanguage: String
    ): List<EngineModelConfig> {
        val stt = models.firstOrNull {
            it.modelType == EngineModelType.STT && it.language == sourceLanguage
        }
        val translation = models.firstOrNull {
            it.modelType == EngineModelType.TRANSLATION &&
                it.language == sourceLanguage &&
                it.targetLanguage == targetLanguage
        }
        val tts = models.firstOrNull {
            it.modelType == EngineModelType.TTS && it.language == targetLanguage
        }
        return listOfNotNull(stt, translation, tts)
    }

    companion object {
        // Model IDs
        const val STT_WHISPER_TINY_TR = "sherpa-stt-whisper-tiny-tr"
        const val STT_WHISPER_TINY_EN = "sherpa-stt-whisper-tiny-en"
        const val STT_WHISPER_BASE_TR = "sherpa-stt-whisper-base-tr"
        const val STT_WHISPER_BASE_EN = "sherpa-stt-whisper-base-en"
        const val TRANSLATION_EN_TR = "opus-mt-en-tr"
        const val TRANSLATION_TR_EN = "opus-mt-tr-en"
        const val TTS_PIPER_TR = "sherpa-tts-piper-tr"
        const val TTS_PIPER_EN = "sherpa-tts-piper-en"
        const val TTS_VITS_TR = "sherpa-tts-vits-tr"
        const val TTS_VITS_EN = "sherpa-tts-vits-en"
    }
}

private fun buildModelCatalog(): List<EngineModelConfig> = listOf(
    // --- STT Models (Whisper via Sherpa-ONNX) ---

    EngineModelConfig(
        modelId = DefaultModelRegistry.STT_WHISPER_TINY_TR,
        modelType = EngineModelType.STT,
        language = "tr",
        backendType = "sherpa-onnx",
        modelFiles = mapOf(
            "encoder" to "whisper-tiny/encoder.onnx",
            "decoder" to "whisper-tiny/decoder.onnx",
            "tokens" to "whisper-tiny/tokens.txt"
        ),
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2",
        sizeBytes = 40 * 1024 * 1024L,
        displayName = "Whisper Tiny TR",
        quantization = ModelQuantization.INT8
    ),
    EngineModelConfig(
        modelId = DefaultModelRegistry.STT_WHISPER_TINY_EN,
        modelType = EngineModelType.STT,
        language = "en",
        backendType = "sherpa-onnx",
        modelFiles = mapOf(
            "encoder" to "whisper-tiny/encoder.onnx",
            "decoder" to "whisper-tiny/decoder.onnx",
            "tokens" to "whisper-tiny/tokens.txt"
        ),
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2",
        sizeBytes = 40 * 1024 * 1024L,
        displayName = "Whisper Tiny EN",
        quantization = ModelQuantization.INT8
    ),
    EngineModelConfig(
        modelId = DefaultModelRegistry.STT_WHISPER_BASE_TR,
        modelType = EngineModelType.STT,
        language = "tr",
        backendType = "sherpa-onnx",
        modelFiles = mapOf(
            "encoder" to "whisper-base/encoder.onnx",
            "decoder" to "whisper-base/decoder.onnx",
            "tokens" to "whisper-base/tokens.txt"
        ),
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-base.tar.bz2",
        sizeBytes = 75 * 1024 * 1024L,
        displayName = "Whisper Base TR",
        quantization = ModelQuantization.FP16
    ),
    EngineModelConfig(
        modelId = DefaultModelRegistry.STT_WHISPER_BASE_EN,
        modelType = EngineModelType.STT,
        language = "en",
        backendType = "sherpa-onnx",
        modelFiles = mapOf(
            "encoder" to "whisper-base/encoder.onnx",
            "decoder" to "whisper-base/decoder.onnx",
            "tokens" to "whisper-base/tokens.txt"
        ),
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-base.tar.bz2",
        sizeBytes = 75 * 1024 * 1024L,
        displayName = "Whisper Base EN",
        quantization = ModelQuantization.FP16
    ),

    // --- Translation Models (OPUS-MT via ONNX Runtime) ---

    EngineModelConfig(
        modelId = DefaultModelRegistry.TRANSLATION_EN_TR,
        modelType = EngineModelType.TRANSLATION,
        language = "en",
        targetLanguage = "tr",
        backendType = "onnx-runtime",
        modelFiles = mapOf(
            "encoder" to "opus-mt-en-tr/encoder_model.onnx",
            "decoder" to "opus-mt-en-tr/decoder_model.onnx",
            "vocab" to "opus-mt-en-tr/source.spm",
            "target_vocab" to "opus-mt-en-tr/target.spm"
        ),
        downloadUrl = "https://huggingface.co/Helsinki-NLP/opus-mt-en-tr/resolve/main/onnx/",
        sizeBytes = 50 * 1024 * 1024L,
        displayName = "OPUS-MT EN→TR",
        quantization = ModelQuantization.INT8
    ),
    EngineModelConfig(
        modelId = DefaultModelRegistry.TRANSLATION_TR_EN,
        modelType = EngineModelType.TRANSLATION,
        language = "tr",
        targetLanguage = "en",
        backendType = "onnx-runtime",
        modelFiles = mapOf(
            "encoder" to "opus-mt-tr-en/encoder_model.onnx",
            "decoder" to "opus-mt-tr-en/decoder_model.onnx",
            "vocab" to "opus-mt-tr-en/source.spm",
            "target_vocab" to "opus-mt-tr-en/target.spm"
        ),
        downloadUrl = "https://huggingface.co/Helsinki-NLP/opus-mt-tr-en/resolve/main/onnx/",
        sizeBytes = 50 * 1024 * 1024L,
        displayName = "OPUS-MT TR→EN",
        quantization = ModelQuantization.INT8
    ),

    // --- TTS Models (Sherpa-ONNX VITS/Piper) ---

    EngineModelConfig(
        modelId = DefaultModelRegistry.TTS_PIPER_TR,
        modelType = EngineModelType.TTS,
        language = "tr",
        backendType = "sherpa-onnx",
        modelFiles = mapOf(
            "model" to "piper-tr/model.onnx",
            "tokens" to "piper-tr/tokens.txt",
            "data_dir" to "piper-tr/espeak-ng-data"
        ),
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-tr_TR-dfki-medium.tar.bz2",
        sizeBytes = 20 * 1024 * 1024L,
        displayName = "Piper TTS TR",
        quantization = ModelQuantization.INT8
    ),
    EngineModelConfig(
        modelId = DefaultModelRegistry.TTS_PIPER_EN,
        modelType = EngineModelType.TTS,
        language = "en",
        backendType = "sherpa-onnx",
        modelFiles = mapOf(
            "model" to "piper-en/model.onnx",
            "tokens" to "piper-en/tokens.txt",
            "data_dir" to "piper-en/espeak-ng-data"
        ),
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-amy-medium.tar.bz2",
        sizeBytes = 20 * 1024 * 1024L,
        displayName = "Piper TTS EN",
        quantization = ModelQuantization.INT8
    ),
    EngineModelConfig(
        modelId = DefaultModelRegistry.TTS_VITS_TR,
        modelType = EngineModelType.TTS,
        language = "tr",
        backendType = "sherpa-onnx",
        modelFiles = mapOf(
            "model" to "vits-tr/model.onnx",
            "lexicon" to "vits-tr/lexicon.txt",
            "tokens" to "vits-tr/tokens.txt"
        ),
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-coqui-tr-medium.tar.bz2",
        sizeBytes = 30 * 1024 * 1024L,
        displayName = "VITS TTS TR",
        quantization = ModelQuantization.FP16
    ),
    EngineModelConfig(
        modelId = DefaultModelRegistry.TTS_VITS_EN,
        modelType = EngineModelType.TTS,
        language = "en",
        backendType = "sherpa-onnx",
        modelFiles = mapOf(
            "model" to "vits-en/model.onnx",
            "lexicon" to "vits-en/lexicon.txt",
            "tokens" to "vits-en/tokens.txt"
        ),
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-ljs.tar.bz2",
        sizeBytes = 30 * 1024 * 1024L,
        displayName = "VITS TTS EN",
        quantization = ModelQuantization.FP16
    )
)
