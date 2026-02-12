package com.jetbrains.kmpapp.speech

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Multi-language speech recognizer
 * Automatically detects language and performs recognition
 */
class MultiLanguageSpeechRecognizer(
    private val speechRecognizer: SpeechRecognizer,
    private val languageDetector: LanguageDetector
) {
    private val _detectedLanguage = MutableStateFlow<RecognitionLanguage?>(null)
    val detectedLanguage: StateFlow<RecognitionLanguage?> = _detectedLanguage

    private var currentLanguage: RecognitionLanguage? = null
    private var isInitialized = false

    /**
     * Supported languages for auto-detection
     */
    var supportedLanguages: List<RecognitionLanguage> = listOf(
        RecognitionLanguage.TURKISH,
        RecognitionLanguage.ENGLISH
    )

    /**
     * Initialize with language detection
     * @param initialAudioSample Initial audio sample for language detection
     * @param config Base recognizer config
     */
    suspend fun initialize(
        initialAudioSample: ShortArray,
        config: RecognizerConfig = RecognizerConfig()
    ): Boolean {
        // Detect language from initial sample
        val detectionResult = languageDetector.detectLanguage(
            audioData = initialAudioSample,
            supportedLanguages = supportedLanguages
        )

        return when (detectionResult) {
            is LanguageDetectionResult.Detected -> {
                currentLanguage = detectionResult.language
                _detectedLanguage.value = detectionResult.language

                // Initialize recognizer with detected language
                val updatedConfig = config.copy(language = detectionResult.language)
                isInitialized = speechRecognizer.initialize(updatedConfig)
                isInitialized
            }
            is LanguageDetectionResult.Unsupported -> {
                _detectedLanguage.value = null
                false
            }
            is LanguageDetectionResult.Error -> {
                _detectedLanguage.value = null
                false
            }
        }
    }

    /**
     * Start recognition with auto-detected language
     */
    fun startRecognition(): Flow<RecognitionResult> {
        return if (isInitialized && currentLanguage != null) {
            speechRecognizer.startRecognition()
        } else {
            kotlinx.coroutines.flow.flow {
                emit(RecognitionResult.Error("Language not detected or recognizer not initialized"))
            }
        }
    }

    /**
     * Process audio data
     */
    suspend fun processAudioData(audioData: ShortArray) {
        if (isInitialized) {
            speechRecognizer.processAudioData(audioData)
        }
    }

    /**
     * Stop recognition
     */
    fun stopRecognition() {
        speechRecognizer.stopRecognition()
    }

    /**
     * Release resources
     */
    fun release() {
        speechRecognizer.release()
        currentLanguage = null
        _detectedLanguage.value = null
        isInitialized = false
    }

    /**
     * Check if recognizer is active
     */
    fun isRecognizing(): Boolean {
        return speechRecognizer.isRecognizing()
    }

    /**
     * Get current detected language
     */
    fun getCurrentLanguage(): RecognitionLanguage? {
        return currentLanguage
    }
}
