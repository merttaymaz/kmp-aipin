package com.jetbrains.kmpapp.pipeline

import com.jetbrains.kmpapp.translation.TranslationResult

/**
 * Configuration for the translation pipeline.
 */
data class PipelineConfig(
    val sourceLanguage: String = "tr",
    val targetLanguage: String = "en",
    val speakTranslation: Boolean = true,
    val continuousMode: Boolean = false,
    /** Base path where model files are stored on the device */
    val modelBasePath: String = ""
)

/**
 * Pipeline execution state.
 * UI observes this to show progress and results.
 */
sealed class PipelineState {
    data object Idle : PipelineState()
    data object Listening : PipelineState()
    data class Recognized(val text: String, val language: String) : PipelineState()
    data object Translating : PipelineState()
    data class Translated(val result: TranslationResult) : PipelineState()
    data object Speaking : PipelineState()
    data class Error(val message: String) : PipelineState()
}
