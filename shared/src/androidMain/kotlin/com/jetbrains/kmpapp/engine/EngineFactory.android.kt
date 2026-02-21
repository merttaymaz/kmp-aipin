package com.jetbrains.kmpapp.engine

import android.app.ActivityManager
import android.content.Context
import com.jetbrains.kmpapp.stt.STTEngine
import com.jetbrains.kmpapp.stt.AndroidSTTEngine
import com.jetbrains.kmpapp.translation.TranslationEngine
import com.jetbrains.kmpapp.translation.AndroidTranslationEngine
import com.jetbrains.kmpapp.tts.TTSEngine
import com.jetbrains.kmpapp.tts.AndroidTTSEngine

/**
 * Android factory: Creates platform-specific engine implementations.
 * These are placeholder implementations that will be filled in during later phases.
 */

actual fun createSTTEngine(): STTEngine = AndroidSTTEngine()

actual fun createTranslationEngine(): TranslationEngine = AndroidTranslationEngine()

actual fun createTTSEngine(): TTSEngine = AndroidTTSEngine()

/**
 * Android 4GB devices use SEQUENTIAL strategy.
 * Detects available RAM and picks the appropriate strategy.
 */
actual fun detectMemoryStrategy(): MemoryStrategy {
    // Android 4GB devices → always SEQUENTIAL for safety
    return MemoryStrategy.SEQUENTIAL
}
