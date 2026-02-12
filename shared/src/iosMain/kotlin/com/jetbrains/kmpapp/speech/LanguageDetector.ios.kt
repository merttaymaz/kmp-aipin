package com.jetbrains.kmpapp.speech

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle

/**
 * iOS language detector using Sherpa-ONNX
 */
@OptIn(ExperimentalForeignApi::class)
class IOSLanguageDetector(
    private val modelsPath: Map<RecognitionLanguage, ModelPaths>
) : LanguageDetector {

    data class ModelPaths(
        val encoder: String,
        val decoder: String,
        val joiner: String,
        val tokens: String
    )

    override suspend fun detectLanguage(
        audioData: ShortArray,
        supportedLanguages: List<RecognitionLanguage>
    ): LanguageDetectionResult {
        if (audioData.isEmpty()) {
            return LanguageDetectionResult.Error("Empty audio data")
        }

        val scores = mutableMapOf<RecognitionLanguage, Float>()

        // Try each supported language
        for (language in supportedLanguages) {
            val modelPath = modelsPath[language]
            if (modelPath == null) {
                continue
            }

            try {
                val score = tryRecognizeWithLanguage(audioData, language, modelPath)
                scores[language] = score
            } catch (e: Exception) {
                // Skip this language if recognition fails
                continue
            }
        }

        if (scores.isEmpty()) {
            return LanguageDetectionResult.Unsupported
        }

        // Find language with highest score
        val detectedLanguage = scores.maxByOrNull { it.value }
        
        return if (detectedLanguage != null && detectedLanguage.value > 0.3f) {
            LanguageDetectionResult.Detected(
                language = detectedLanguage.key,
                confidence = detectedLanguage.value
            )
        } else {
            LanguageDetectionResult.Unsupported
        }
    }

    override suspend fun detectLanguageFromStream(
        audioData: ShortArray,
        durationSeconds: Int,
        supportedLanguages: List<RecognitionLanguage>
    ): LanguageDetectionResult {
        val sampleRate = 16000
        val maxSamples = sampleRate * durationSeconds
        val samples = if (audioData.size > maxSamples) {
            audioData.take(maxSamples).toShortArray()
        } else {
            audioData
        }

        return detectLanguage(samples, supportedLanguages)
    }

    private fun tryRecognizeWithLanguage(
        audioData: ShortArray,
        language: RecognitionLanguage,
        modelPath: ModelPaths
    ): Float {
        // iOS implementation would use Sherpa-ONNX C API
        // This is a placeholder - actual implementation depends on C bindings
        
        // Basic heuristic for now:
        // - Check if model files exist
        // - Return a score based on audio characteristics
        
        val textLength = (audioData.size / 16000.0 * 3).toInt() // Estimate
        val score = if (textLength > 0) 0.7f else 0.1f
        
        return score
    }

    private fun calculateConfidenceScore(text: String, audioLength: Int): Float {
        if (text.isEmpty()) return 0f

        val textLength = text.length
        val expectedLength = (audioLength / 16000.0 * 5).toInt()
        
        val lengthScore = when {
            textLength == 0 -> 0f
            textLength < expectedLength / 2 -> 0.3f
            textLength > expectedLength * 2 -> 0.5f
            else -> 0.8f
        }

        return lengthScore
    }
}

/**
 * Create iOS language detector
 */
actual fun createLanguageDetector(): LanguageDetector {
    val bundle = NSBundle.mainBundle
    
    val modelsPath = mapOf(
        RecognitionLanguage.TURKISH to IOSLanguageDetector.ModelPaths(
            encoder = bundle.pathForResource("encoder", ofType = "onnx", inDirectory = "sherpa-onnx/turkish") ?: "",
            decoder = bundle.pathForResource("decoder", ofType = "onnx", inDirectory = "sherpa-onnx/turkish") ?: "",
            joiner = bundle.pathForResource("joiner", ofType = "onnx", inDirectory = "sherpa-onnx/turkish") ?: "",
            tokens = bundle.pathForResource("tokens", ofType = "txt", inDirectory = "sherpa-onnx/turkish") ?: ""
        ),
        RecognitionLanguage.ENGLISH to IOSLanguageDetector.ModelPaths(
            encoder = bundle.pathForResource("encoder", ofType = "onnx", inDirectory = "sherpa-onnx/english") ?: "",
            decoder = bundle.pathForResource("decoder", ofType = "onnx", inDirectory = "sherpa-onnx/english") ?: "",
            joiner = bundle.pathForResource("joiner", ofType = "onnx", inDirectory = "sherpa-onnx/english") ?: "",
            tokens = bundle.pathForResource("tokens", ofType = "txt", inDirectory = "sherpa-onnx/english") ?: ""
        )
    )

    return IOSLanguageDetector(modelsPath)
}
