package com.jetbrains.kmpapp.engine

import com.jetbrains.kmpapp.stt.STTEngine
import com.jetbrains.kmpapp.stt.IOSSTTEngine
import com.jetbrains.kmpapp.translation.TranslationEngine
import com.jetbrains.kmpapp.translation.IOSTranslationEngine
import com.jetbrains.kmpapp.tts.TTSEngine
import com.jetbrains.kmpapp.tts.IOSTTSEngine

/**
 * iOS factory: Creates platform-specific engine implementations.
 * These are placeholder implementations that will be filled in during later phases.
 */

actual fun createSTTEngine(): STTEngine = IOSSTTEngine()

actual fun createTranslationEngine(): TranslationEngine = IOSTranslationEngine()

actual fun createTTSEngine(): TTSEngine = IOSTTSEngine()

/**
 * iOS (6-8GB RAM on modern iPhones) uses CONCURRENT strategy.
 * All models can be kept in RAM simultaneously.
 */
actual fun detectMemoryStrategy(): MemoryStrategy {
    return MemoryStrategy.CONCURRENT
}
