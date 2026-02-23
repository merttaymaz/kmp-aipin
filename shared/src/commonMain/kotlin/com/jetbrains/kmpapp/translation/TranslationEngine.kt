package com.jetbrains.kmpapp.translation

import com.jetbrains.kmpapp.engine.Engine

/**
 * Translation engine interface.
 * Uses OPUS-MT models via ONNX Runtime on both Android and iOS.
 */
interface TranslationEngine : Engine {
    /** Translate text from source to target language */
    suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): TranslationResult

    /** Get supported language pairs for this engine instance */
    fun getSupportedLanguagePairs(): List<LanguagePair>
}

data class TranslationResult(
    val translatedText: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val confidence: Float? = null
)

data class LanguagePair(
    val source: String,
    val target: String
)
