package com.jetbrains.kmpapp.speech

import kotlinx.coroutines.flow.Flow

/**
 * Speech recognition result
 */
sealed class RecognitionResult {
    data class Partial(val text: String) : RecognitionResult()
    data class Final(val text: String) : RecognitionResult()
    data class Error(val message: String, val exception: Throwable? = null) : RecognitionResult()
}

/**
 * Supported languages for speech recognition
 */
enum class RecognitionLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    TURKISH("tr", "Turkish"),
    GERMAN("de", "German"),
    FRENCH("fr", "French"),
    SPANISH("es", "Spanish"),
    ITALIAN("it", "Italian"),
    RUSSIAN("ru", "Russian"),
    CHINESE("zh", "Chinese"),
    JAPANESE("ja", "Japanese"),
    KOREAN("ko", "Korean"),
    ARABIC("ar", "Arabic")
}

/**
 * Model type for speech recognition
 */
enum class ModelType {
    ZIPFORMER,  // Fast, streaming, single language
    WHISPER     // Slower, multilingual, high accuracy
}

/**
 * Speech recognizer configuration
 */
data class RecognizerConfig(
    val language: RecognitionLanguage = RecognitionLanguage.ENGLISH,
    val sampleRate: Int = 16000,
    val enablePunctuation: Boolean = true,
    val maxAlternatives: Int = 1,
    val modelType: ModelType = ModelType.WHISPER  // Default to Whisper for multilingual support
)

/**
 * Speech recognizer interface using Sherpa-ONNX
 * On-device speech recognition without internet connection
 */
interface SpeechRecognizer {
    /**
     * Initialize the recognizer with configuration
     * Must be called before using the recognizer
     */
    suspend fun initialize(config: RecognizerConfig): Boolean

    /**
     * Start recognition session
     * @return Flow of recognition results
     */
    fun startRecognition(): Flow<RecognitionResult>

    /**
     * Stop recognition session
     */
    fun stopRecognition()

    /**
     * Process audio data for recognition
     * @param audioData PCM audio data (16-bit, mono)
     */
    suspend fun processAudioData(audioData: ShortArray)

    /**
     * Check if recognizer is currently active
     */
    fun isRecognizing(): Boolean

    /**
     * Release resources
     */
    fun release()

    /**
     * Check if model is available for the given language
     */
    suspend fun isModelAvailable(language: RecognitionLanguage): Boolean

    /**
     * Download model for the given language
     * @return true if download successful
     */
    suspend fun downloadModel(
        language: RecognitionLanguage,
        onProgress: ((current: Long, total: Long) -> Unit)? = null
    ): Boolean
}

/**
 * Platform-specific speech recognizer factory
 */
expect fun createSpeechRecognizer(): SpeechRecognizer
