package com.jetbrains.kmpapp.pipeline

import com.jetbrains.kmpapp.audio.AudioPlayer
import com.jetbrains.kmpapp.engine.MemoryStrategy
import com.jetbrains.kmpapp.stt.STTConfig
import com.jetbrains.kmpapp.stt.STTEngine
import com.jetbrains.kmpapp.translation.TranslationEngine
import com.jetbrains.kmpapp.translation.TranslationResult
import com.jetbrains.kmpapp.tts.TTSConfig
import com.jetbrains.kmpapp.tts.TTSEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Orchestrates the full STT → Translation → TTS pipeline.
 *
 * On Android (4GB RAM): Uses sequential model loading.
 *   Load STT → recognize → release STT → load Translation → translate → release → load TTS → speak → release
 *
 * On iOS (6-8GB RAM): Uses concurrent model loading.
 *   All models preloaded → recognize → translate → speak (no loading delays)
 */
class TranslationPipeline(
    private val sttEngine: STTEngine,
    private val translationEngine: TranslationEngine,
    private val ttsEngine: TTSEngine,
    private val audioPlayer: AudioPlayer,
    private val memoryStrategy: MemoryStrategy
) {
    private val _state = MutableStateFlow<PipelineState>(PipelineState.Idle)
    val state: StateFlow<PipelineState> = _state.asStateFlow()

    private var isRunning = false

    /**
     * Initialize pipeline. On CONCURRENT strategy, preloads all models.
     */
    suspend fun initialize(config: PipelineConfig) {
        if (memoryStrategy == MemoryStrategy.CONCURRENT) {
            preloadAllModels(config)
        }
    }

    /**
     * Start the full pipeline: listen → recognize → translate → speak
     */
    suspend fun start(config: PipelineConfig) {
        if (isRunning) return
        isRunning = true

        try {
            when (memoryStrategy) {
                MemoryStrategy.SEQUENTIAL -> executeSequential(config)
                MemoryStrategy.CONCURRENT -> executeConcurrent(config)
            }
        } catch (e: Exception) {
            _state.value = PipelineState.Error(e.message ?: "Pipeline error")
        } finally {
            isRunning = false
        }
    }

    fun stop() {
        isRunning = false
        sttEngine.stopRecognition()
        ttsEngine.stop()
        audioPlayer.stop()
        _state.value = PipelineState.Idle
    }

    /**
     * Translate text without STT (text input mode).
     */
    suspend fun translateText(
        text: String,
        from: String,
        to: String,
        speakResult: Boolean = true
    ): TranslationResult {
        _state.value = PipelineState.Translating

        if (memoryStrategy == MemoryStrategy.SEQUENTIAL) {
            translationEngine.initialize("opus-mt-$from-$to")
        }

        val result = translationEngine.translate(text, from, to)
        _state.value = PipelineState.Translated(result)

        if (speakResult) {
            _state.value = PipelineState.Speaking

            if (memoryStrategy == MemoryStrategy.SEQUENTIAL) {
                translationEngine.release()
                ttsEngine.initialize("sherpa-tts-piper-$to")
            }

            val audio = ttsEngine.synthesize(result.translatedText, TTSConfig(language = to))
            audioPlayer.play(audio.audioData, audio.sampleRate)

            if (memoryStrategy == MemoryStrategy.SEQUENTIAL) {
                ttsEngine.release()
            }
        } else if (memoryStrategy == MemoryStrategy.SEQUENTIAL) {
            translationEngine.release()
        }

        _state.value = PipelineState.Idle
        return result
    }

    suspend fun release() {
        stop()
        sttEngine.release()
        translationEngine.release()
        ttsEngine.release()
        audioPlayer.release()
    }

    // --- Sequential pipeline (Android 4GB) ---

    private suspend fun executeSequential(config: PipelineConfig) {
        // Phase 1: STT
        _state.value = PipelineState.Listening
        sttEngine.initialize(
            getSTTModelId(config.sourceLanguage),
            buildSTTConfig(config)
        )
        val recognizedText = collectRecognition(config)
        sttEngine.release()

        if (recognizedText.isBlank()) {
            _state.value = PipelineState.Idle
            return
        }

        _state.value = PipelineState.Recognized(recognizedText, config.sourceLanguage)

        // Phase 2: Translation
        _state.value = PipelineState.Translating
        translationEngine.initialize(
            "opus-mt-${config.sourceLanguage}-${config.targetLanguage}"
        )
        val translated = translationEngine.translate(
            recognizedText, config.sourceLanguage, config.targetLanguage
        )
        translationEngine.release()

        _state.value = PipelineState.Translated(translated)

        // Phase 3: TTS
        if (config.speakTranslation) {
            _state.value = PipelineState.Speaking
            ttsEngine.initialize(getTTSModelId(config.targetLanguage))
            val audio = ttsEngine.synthesize(
                translated.translatedText,
                TTSConfig(language = config.targetLanguage)
            )
            audioPlayer.play(audio.audioData, audio.sampleRate)
            ttsEngine.release()
        }

        _state.value = PipelineState.Idle
    }

    // --- Concurrent pipeline (iOS 6-8GB) ---

    private suspend fun executeConcurrent(config: PipelineConfig) {
        // Phase 1: STT (already loaded)
        _state.value = PipelineState.Listening
        val recognizedText = collectRecognition(config)

        if (recognizedText.isBlank()) {
            _state.value = PipelineState.Idle
            return
        }

        _state.value = PipelineState.Recognized(recognizedText, config.sourceLanguage)

        // Phase 2: Translation (already loaded)
        _state.value = PipelineState.Translating
        val translated = translationEngine.translate(
            recognizedText, config.sourceLanguage, config.targetLanguage
        )
        _state.value = PipelineState.Translated(translated)

        // Phase 3: TTS (already loaded)
        if (config.speakTranslation) {
            _state.value = PipelineState.Speaking
            val audio = ttsEngine.synthesize(
                translated.translatedText,
                TTSConfig(language = config.targetLanguage)
            )
            audioPlayer.play(audio.audioData, audio.sampleRate)
        }

        _state.value = PipelineState.Idle
    }

    private suspend fun preloadAllModels(config: PipelineConfig) {
        sttEngine.initialize(
            getSTTModelId(config.sourceLanguage),
            buildSTTConfig(config)
        )
        translationEngine.initialize(
            "opus-mt-${config.sourceLanguage}-${config.targetLanguage}"
        )
        ttsEngine.initialize(getTTSModelId(config.targetLanguage))
    }

    private suspend fun collectRecognition(config: PipelineConfig): String {
        val sttConfig = STTConfig(
            language = config.sourceLanguage,
            sampleRate = 16000
        )
        var finalText = ""
        sttEngine.startRecognition(sttConfig).collect { result ->
            if (result.isFinal) {
                finalText = result.text
                sttEngine.stopRecognition()
            }
        }
        return finalText
    }

    private fun getSTTModelId(language: String): String =
        "sherpa-stt-whisper-tiny-$language"

    private fun getTTSModelId(language: String): String =
        "sherpa-tts-piper-$language"

    /**
     * Build config map for STT engine initialization.
     * Provides modelBasePath, modelType, and language so the engine
     * can locate and load the correct Sherpa-ONNX model files.
     */
    private fun buildSTTConfig(config: PipelineConfig): Map<String, Any> = mapOf(
        "modelBasePath" to config.modelBasePath,
        "modelType" to "whisper",
        "language" to config.sourceLanguage
    )
}
