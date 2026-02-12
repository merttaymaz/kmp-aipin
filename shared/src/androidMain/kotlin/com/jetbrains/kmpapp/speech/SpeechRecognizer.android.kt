package com.jetbrains.kmpapp.speech

import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

/**
 * Android implementation using Sherpa-ONNX
 * Supports both Zipformer and Whisper models
 */
class AndroidSpeechRecognizer(
    private val modelBasePath: String
) : SpeechRecognizer {

    private var onlineRecognizer: OnlineRecognizer? = null
    private var offlineRecognizer: OfflineRecognizer? = null
    private var stream: OnlineStream? = null
    private var config: RecognizerConfig? = null
    private var isRecognizingFlag = false

    override suspend fun initialize(config: RecognizerConfig): Boolean {
        return try {
            this.config = config

            when (config.modelType) {
                ModelType.ZIPFORMER -> initializeZipformer(config)
                ModelType.WHISPER -> initializeWhisper(config)
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun initializeZipformer(config: RecognizerConfig): Boolean {
        val recognizerConfig = OnlineRecognizerConfig(
            featConfig = FeatureConfig(
                sampleRate = config.sampleRate,
                featureDim = 80
            ),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "$modelBasePath/encoder.onnx",
                    decoder = "$modelBasePath/decoder.onnx",
                    joiner = "$modelBasePath/joiner.onnx"
                ),
                tokens = "$modelBasePath/tokens.txt",
                modelType = "zipformer2"
            ),
            enableEndpoint = true,
            maxActivePaths = config.maxAlternatives,
            decodingMethod = "greedy_search"
        )

        onlineRecognizer = OnlineRecognizer(recognizerConfig)
        return true
    }

    private fun initializeWhisper(config: RecognizerConfig): Boolean {
        val recognizerConfig = OfflineRecognizerConfig(
            featConfig = OfflineFeatureConfig(
                sampleRate = config.sampleRate,
                featureDim = 80
            ),
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = "$modelBasePath/whisper-encoder.onnx",
                    decoder = "$modelBasePath/whisper-decoder.onnx",
                    language = config.language.code,
                    task = "transcribe"
                ),
                tokens = "$modelBasePath/whisper-tokens.txt",
                modelType = "whisper"
            ),
            decodingMethod = "greedy_search"
        )

        offlineRecognizer = OfflineRecognizer(recognizerConfig)
        return true
    }

    override fun startRecognition(): Flow<RecognitionResult> = callbackFlow {
        try {
            when (config?.modelType) {
                ModelType.ZIPFORMER -> {
                    if (onlineRecognizer == null) {
                        trySend(RecognitionResult.Error("Recognizer not initialized"))
                        close()
                        return@callbackFlow
                    }

                    stream = onlineRecognizer!!.createStream()
                    isRecognizingFlag = true
                    trySend(RecognitionResult.Partial(""))
                }
                ModelType.WHISPER -> {
                    if (offlineRecognizer == null) {
                        trySend(RecognitionResult.Error("Recognizer not initialized"))
                        close()
                        return@callbackFlow
                    }
                    isRecognizingFlag = true
                }
                null -> {
                    trySend(RecognitionResult.Error("Config not set"))
                    close()
                    return@callbackFlow
                }
            }

            awaitClose {
                stopRecognition()
            }
        } catch (e: Exception) {
            trySend(RecognitionResult.Error("Failed to start recognition", e))
            close()
        }
    }

    override fun stopRecognition() {
        isRecognizingFlag = false
        stream?.let { stream ->
            onlineRecognizer?.let { recognizer ->
                while (recognizer.isReady(stream)) {
                    recognizer.decode(stream)
                }
            }
        }
        stream?.release()
        stream = null
    }

    override suspend fun processAudioData(audioData: ShortArray) {
        try {
            if (!isRecognizingFlag) return

            when (config?.modelType) {
                ModelType.ZIPFORMER -> processZipformer(audioData)
                ModelType.WHISPER -> processWhisper(audioData)
                null -> {}
            }
        } catch (e: Exception) {
            // Handle error
        }
    }

    private fun processZipformer(audioData: ShortArray) {
        val currentStream = stream ?: return
        val currentRecognizer = onlineRecognizer ?: return

        // Convert to float samples
        currentStream.acceptWaveform(
            samples = audioData.map { it.toFloat() / 32768.0f }.toFloatArray(),
            sampleRate = config?.sampleRate ?: 16000
        )

        // Decode
        while (currentRecognizer.isReady(currentStream)) {
            currentRecognizer.decode(currentStream)
        }

        val result = currentRecognizer.getResult(currentStream)

        if (result.text.isNotEmpty()) {
            if (currentRecognizer.isEndpoint(currentStream)) {
                // Final result
                currentRecognizer.reset(currentStream)
            }
        }
    }

    private fun processWhisper(audioData: ShortArray) {
        val currentRecognizer = offlineRecognizer ?: return

        // Whisper processes complete audio segments
        val floatSamples = audioData.map { it.toFloat() / 32768.0f }.toFloatArray()

        val stream = currentRecognizer.createStream()
        stream.acceptWaveform(floatSamples, config?.sampleRate ?: 16000)
        currentRecognizer.decode(stream)

        val result = currentRecognizer.getResult(stream)
        stream.release()

        // Result handling would emit via callback
    }

    override fun isRecognizing(): Boolean = isRecognizingFlag

    override fun release() {
        stopRecognition()
        onlineRecognizer?.release()
        offlineRecognizer?.release()
        onlineRecognizer = null
        offlineRecognizer = null
    }

    override suspend fun isModelAvailable(language: RecognitionLanguage): Boolean {
        val encoderFile = File("$modelBasePath/encoder.onnx")
        val whisperEncoderFile = File("$modelBasePath/whisper-encoder.onnx")
        return encoderFile.exists() || whisperEncoderFile.exists()
    }

    override suspend fun downloadModel(
        language: RecognitionLanguage,
        onProgress: ((current: Long, total: Long) -> Unit)?
    ): Boolean {
        // Model download logic
        return false
    }
}

/**
 * Create Android speech recognizer
 */
actual fun createSpeechRecognizer(): SpeechRecognizer {
    return AndroidSpeechRecognizer(
        modelBasePath = "" // Will be set from assets
    )
}
