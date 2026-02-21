package com.jetbrains.kmpapp.engine

import com.jetbrains.kmpapp.stt.STTEngine
import com.jetbrains.kmpapp.stt.AndroidSTTEngine
import com.jetbrains.kmpapp.translation.TranslationEngine
import com.jetbrains.kmpapp.translation.AndroidTranslationEngine
import com.jetbrains.kmpapp.tts.TTSEngine
import com.jetbrains.kmpapp.tts.AndroidTTSEngine

/**
 * Android factory: Creates platform-specific engine implementations.
 * STT engine uses Sherpa-ONNX JNI bindings for Whisper/Zipformer models.
 */

actual fun createSTTEngine(): STTEngine = AndroidSTTEngine()

actual fun createTranslationEngine(): TranslationEngine = AndroidTranslationEngine()

actual fun createTTSEngine(): TTSEngine = AndroidTTSEngine()

/**
 * Android 4GB devices use SEQUENTIAL strategy.
 * Load one model at a time, release before loading next.
 */
actual fun detectMemoryStrategy(): MemoryStrategy {
    return MemoryStrategy.SEQUENTIAL
}
