package com.jetbrains.kmpapp.model

/**
 * Type of AI engine model.
 * Used by ModelRegistry to categorize and filter available models.
 */
enum class EngineModelType {
    STT,
    TTS,
    TRANSLATION
}
