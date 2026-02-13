package com.jetbrains.kmpapp.speech

import com.k2fsa.sherpa.onnx.*

/**
 * Android language detector using Sherpa-ONNX
 * Uses multiple small recognition attempts to detect language
 */
class AndroidLanguageDetector(
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
        // Use longer audio for more accurate detection
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
        var recognizer: OnlineRecognizer? = null
        var stream: OnlineStream? = null

        try {
            // Create recognizer for this language
            val config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(
                    sampleRate = 16000,
                    featureDim = 80
                ),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = modelPath.encoder,
                        decoder = modelPath.decoder,
                        joiner = modelPath.joiner
                    ),
                    tokens = modelPath.tokens,
                    modelType = "zipformer2"
                ),
                enableEndpoint = true,
                maxActivePaths = 4,
                decodingMethod = "greedy_search"
            )

            recognizer = OnlineRecognizer(assetManager = null, config = config)
            stream = recognizer.createStream()

            // Process audio
            val floatSamples = audioData.map { it.toFloat() / 32768.0f }.toFloatArray()
            stream.acceptWaveform(floatSamples, 16000)

            // Decode
            var decodeCount = 0
            while (recognizer.isReady(stream) && decodeCount < 10) {
                recognizer.decode(stream)
                decodeCount++
            }

            val result = recognizer.getResult(stream)
            
            // Calculate confidence score based on result
            val score = calculateConfidenceScore(result.text, audioData.size)
            
            return score
        } catch (e: Exception) {
            return 0f
        } finally {
            stream?.release()
            recognizer?.release()
        }
    }

    private fun calculateConfidenceScore(text: String, audioLength: Int): Float {
        if (text.isEmpty()) return 0f

        // Basic scoring:
        // - Non-empty result = good sign
        // - Longer text = more confidence
        // - Reasonable text length for audio duration = better
        
        val textLength = text.length
        val expectedLength = (audioLength / 16000.0 * 5).toInt() // ~5 chars per second
        
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
 * Create Android language detector
 */
actual fun createLanguageDetector(): LanguageDetector {
    // Model paths will be set from assets
    val modelsPath = mapOf(
        RecognitionLanguage.TURKISH to AndroidLanguageDetector.ModelPaths(
            encoder = "sherpa-onnx/turkish/encoder.onnx",
            decoder = "sherpa-onnx/turkish/decoder.onnx",
            joiner = "sherpa-onnx/turkish/joiner.onnx",
            tokens = "sherpa-onnx/turkish/tokens.txt"
        ),
        RecognitionLanguage.ENGLISH to AndroidLanguageDetector.ModelPaths(
            encoder = "sherpa-onnx/english/encoder.onnx",
            decoder = "sherpa-onnx/english/decoder.onnx",
            joiner = "sherpa-onnx/english/joiner.onnx",
            tokens = "sherpa-onnx/english/tokens.txt"
        )
    )

    return AndroidLanguageDetector(modelsPath)
}
