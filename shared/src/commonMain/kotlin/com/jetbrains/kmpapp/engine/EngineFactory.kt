package com.jetbrains.kmpapp.engine

import com.jetbrains.kmpapp.stt.STTEngine
import com.jetbrains.kmpapp.translation.TranslationEngine
import com.jetbrains.kmpapp.tts.TTSEngine

/**
 * Platform-specific engine factory functions.
 * Each platform provides its own implementation via expect/actual.
 */
expect fun createSTTEngine(): STTEngine

expect fun createTranslationEngine(): TranslationEngine

expect fun createTTSEngine(): TTSEngine

/**
 * Detect the appropriate memory strategy for the current device.
 * Android 4GB → SEQUENTIAL, iOS 6-8GB → CONCURRENT.
 */
expect fun detectMemoryStrategy(): MemoryStrategy
