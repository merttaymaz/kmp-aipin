package com.jetbrains.kmpapp.speech

/**
 * Language detection result
 */
sealed class LanguageDetectionResult {
    data class Detected(val language: RecognitionLanguage, val confidence: Float) : LanguageDetectionResult()
    data object Unsupported : LanguageDetectionResult()
    data class Error(val message: String) : LanguageDetectionResult()
}

/**
 * Language detector interface
 * Detects spoken language from audio samples
 */
interface LanguageDetector {
    /**
     * Detect language from audio samples
     * @param audioData PCM audio data (16-bit, mono)
     * @param supportedLanguages List of languages to detect (default: Turkish, English)
     * @return Language detection result
     */
    suspend fun detectLanguage(
        audioData: ShortArray,
        supportedLanguages: List<RecognitionLanguage> = listOf(
            RecognitionLanguage.TURKISH,
            RecognitionLanguage.ENGLISH
        )
    ): LanguageDetectionResult

    /**
     * Detect language from longer audio stream
     * More accurate but takes more time
     */
    suspend fun detectLanguageFromStream(
        audioData: ShortArray,
        durationSeconds: Int = 3,
        supportedLanguages: List<RecognitionLanguage> = listOf(
            RecognitionLanguage.TURKISH,
            RecognitionLanguage.ENGLISH
        )
    ): LanguageDetectionResult
}

/**
 * Platform-specific language detector factory
 */
expect fun createLanguageDetector(): LanguageDetector
